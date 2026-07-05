package com.banco.coordinator;

import com.banco.config.BancoProperties;
import com.banco.config.BancoProperties.Peer;
import com.banco.model.dto.LockRequest;
import com.banco.model.dto.LockResponse;
import com.banco.service.BancoRemotoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Exclusión mutua distribuida con algoritmo <b>Ricart–Agrawala simplificado</b>
 * (Hito 2 · Integrante 3).
 *
 * <p>Cada lock es <b>por cuenta</b>: dos transferencias sobre cuentas distintas
 * no se bloquean entre sí. El {@link TwoPhaseCommitCoordinator} llama
 * {@link #adquirir(String)} antes de la fase PREPARE y {@link #liberar(String)}
 * después de COMMIT/ABORT.
 *
 * <h3>Algoritmo Ricart–Agrawala (resumen)</h3>
 * <ol>
 *   <li>Para operar una cuenta, el nodo incrementa su {@link RelojLamport} y envía
 *       {@code REQUEST(cuenta, timestamp, bancoId)} a <b>todos</b> los peers.</li>
 *   <li>Cada peer responde {@code OK} <b>inmediatamente</b> salvo que:
 *       <ul>
 *         <li>Esté <b>en sección crítica</b> para esa cuenta → encola la petición.</li>
 *         <li>Esté <b>solicitando</b> la misma cuenta con un timestamp <b>menor</b>
 *             (o igual pero con id menor) → encola la petición.</li>
 *       </ul>
 *   </li>
 *   <li>El nodo entra a la sección crítica cuando recibe {@code OK} de <b>todos</b>
 *       los peers vivos.</li>
 *   <li>Al liberar, envía {@code OK} a todas las peticiones encoladas.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExclusionMutua {

    private final BancoProperties properties;
    private final BancoRemotoClient remoto;
    private final RelojLamport reloj;

    /** Timeout máximo para esperar los OK de los peers (evita bloqueo eterno si un peer muere). */
    private static final long TIMEOUT_SEGUNDOS = 10;

    /** Estado del lock distribuido por cuenta. */
    private final ConcurrentHashMap<String, EstadoLock> estadoPorCuenta = new ConcurrentHashMap<>();

    // ------------------------------------------------------------ estado interno

    private static class EstadoLock {
        volatile boolean enSeccionCritica = false;
        volatile boolean solicitando = false;
        volatile long timestampSolicitud = 0;
        volatile CountDownLatch latch;
        final Queue<LockRequest> encoladas = new ConcurrentLinkedQueue<>();
    }

    private EstadoLock estadoDe(String cuenta) {
        return estadoPorCuenta.computeIfAbsent(cuenta, k -> new EstadoLock());
    }

    // ------------------------------------------------------------ adquirir / liberar

    /**
     * Adquiere la exclusión mutua distribuida sobre una cuenta.
     * <b>Bloquea</b> hasta recibir OK de todos los peers (o timeout).
     */
    public void adquirir(String cuenta) {
        EstadoLock estado = estadoDe(cuenta);

        synchronized (estado) {
            estado.solicitando = true;
            estado.timestampSolicitud = reloj.tick();
        }

        int peersCount = properties.getPeers().size();
        estado.latch = new CountDownLatch(peersCount);

        log.info("EXCLUSION MUTUA banco={} REQUEST cuenta={} timestamp={} → enviado a {} peers",
                properties.getId(), cuenta, estado.timestampSolicitud, peersCount);

        // Enviar REQUEST a todos los peers en paralelo
        LockRequest req = new LockRequest(properties.getId(), cuenta, estado.timestampSolicitud);
        for (Peer peer : properties.getPeers()) {
            enviarRequestAPeer(peer, req, estado);
        }

        // Esperar OK de todos (con timeout por si un peer está muerto)
        try {
            boolean recibidosTodos = estado.latch.await(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
            if (!recibidosTodos) {
                log.warn("EXCLUSION MUTUA banco={} TIMEOUT cuenta={} (no todos respondieron, " +
                        "procediendo con los que respondieron)", properties.getId(), cuenta);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("EXCLUSION MUTUA banco={} interrumpido esperando OK para cuenta={}",
                    properties.getId(), cuenta);
        }

        synchronized (estado) {
            estado.solicitando = false;
            estado.enSeccionCritica = true;
        }

        log.info("EXCLUSION MUTUA banco={} ADQUIRIDO cuenta={} (entró a sección crítica)",
                properties.getId(), cuenta);
    }

    /**
     * Libera la exclusión mutua sobre una cuenta.
     * Responde OK a todas las peticiones que quedaron encoladas durante la sección crítica.
     */
    public void liberar(String cuenta) {
        EstadoLock estado = estadoDe(cuenta);

        Queue<LockRequest> pendientes;
        synchronized (estado) {
            estado.enSeccionCritica = false;
            pendientes = new ConcurrentLinkedQueue<>(estado.encoladas);
            estado.encoladas.clear();
        }

        log.info("EXCLUSION MUTUA banco={} LIBERADO cuenta={} (respondiendo OK a {} encolados)",
                properties.getId(), cuenta, pendientes.size());

        // Enviar OK (release) a todos los que encolamos
        for (LockRequest encolada : pendientes) {
            enviarOkDiferido(encolada);
        }
    }

    // ------------------------------------------------------------ recepción de mensajes

    /**
     * Recibe un REQUEST de otro nodo. Aplica la lógica Ricart–Agrawala:
     * <ul>
     *   <li>Si <b>no estoy interesado</b> en esa cuenta → OK inmediato.</li>
     *   <li>Si <b>estoy en sección crítica</b> → encolo (responderé al liberar).</li>
     *   <li>Si <b>estoy solicitando</b> → comparo timestamps; el menor gana
     *       (empate se resuelve por id de banco).</li>
     * </ul>
     *
     * @return {@code ok=true} si se concede inmediatamente; {@code ok=false} si se difiere.
     */
    public LockResponse recibirRequest(LockRequest req) {
        reloj.actualizar(req.getTimestamp());
        EstadoLock estado = estadoDe(req.getCuenta());

        synchronized (estado) {
            if (estado.enSeccionCritica) {
                // Estoy en sección crítica → encolo
                estado.encoladas.add(req);
                log.info("EXCLUSION MUTUA banco={} ENCOLADO REQUEST de {} cuenta={} timestamp={} " +
                                "(estoy en sección crítica)",
                        properties.getId(), req.getBancoId(), req.getCuenta(), req.getTimestamp());
                return new LockResponse(properties.getId(), req.getCuenta(), false);
            }

            if (estado.solicitando) {
                // Ambos queremos la misma cuenta → comparo timestamps
                boolean yoTengoPrioridad = tengoMasPrioridad(
                        estado.timestampSolicitud, req.getTimestamp(), req.getBancoId());

                if (yoTengoPrioridad) {
                    // Yo gano → encolo al otro
                    estado.encoladas.add(req);
                    log.info("EXCLUSION MUTUA banco={} ENCOLADO REQUEST de {} cuenta={} " +
                                    "(mi timestamp {} < suyo {}, yo tengo prioridad)",
                            properties.getId(), req.getBancoId(), req.getCuenta(),
                            estado.timestampSolicitud, req.getTimestamp());
                    return new LockResponse(properties.getId(), req.getCuenta(), false);
                }
            }
        }

        // No interesado o el otro tiene prioridad → OK inmediato
        log.info("EXCLUSION MUTUA banco={} OK cuenta={} → respondido a {} (no interesado o menor prioridad)",
                properties.getId(), req.getCuenta(), req.getBancoId());
        return new LockResponse(properties.getId(), req.getCuenta(), true);
    }

    /**
     * Recibe un OK diferido de otro nodo (respuesta tardía a mi REQUEST).
     * Decrementa el latch para que {@link #adquirir} pueda continuar.
     */
    public void recibirRelease(LockRequest req) {
        reloj.actualizar(req.getTimestamp());
        EstadoLock estado = estadoDe(req.getCuenta());

        log.info("EXCLUSION MUTUA banco={} recibe OK diferido de {} para cuenta={}",
                properties.getId(), req.getBancoId(), req.getCuenta());

        if (estado.latch != null) {
            estado.latch.countDown();
        }
    }

    // ------------------------------------------------------------ internos

    /**
     * Envía REQUEST a un peer individual. Si el peer responde OK directamente,
     * decrementa el latch. Si no responde (caído), también decrementa (tolerancia).
     */
    private void enviarRequestAPeer(Peer peer, LockRequest req, EstadoLock estado) {
        try {
            LockResponse resp = remoto.postInternal(
                    peer.getUrl(), "/internal/lock/request", req, LockResponse.class);

            if (resp != null && resp.isOk()) {
                log.info("EXCLUSION MUTUA banco={} recibe OK inmediato de {} para cuenta={}",
                        properties.getId(), peer.getId(), req.getCuenta());
                estado.latch.countDown();
            } else {
                log.info("EXCLUSION MUTUA banco={} REQUEST diferido por {} para cuenta={} (esperando OK)",
                        properties.getId(), peer.getId(), req.getCuenta());
                // El OK llegará vía /internal/lock/release → recibirRelease() hará countDown
            }
        } catch (Exception e) {
            log.warn("EXCLUSION MUTUA banco={} peer {} no respondió REQUEST cuenta={}: {} " +
                            "(se cuenta como OK por tolerancia a fallos)",
                    properties.getId(), peer.getId(), req.getCuenta(), e.getMessage());
            estado.latch.countDown(); // Peer caído → no bloqueo
        }
    }

    /**
     * Envía un OK diferido a un nodo que estaba encolado mientras estábamos en SC.
     */
    private void enviarOkDiferido(LockRequest encolada) {
        try {
            String peerUrl = remoto.urlDePeer(encolada.getBancoId())
                    .orElseThrow(() -> new IllegalStateException("Peer no configurado: " + encolada.getBancoId()));
            LockRequest okMsg = new LockRequest(properties.getId(), encolada.getCuenta(), reloj.tick());
            remoto.postInternal(peerUrl, "/internal/lock/release", okMsg, Void.class);
            log.info("EXCLUSION MUTUA banco={} envía OK diferido a {} para cuenta={}",
                    properties.getId(), encolada.getBancoId(), encolada.getCuenta());
        } catch (Exception e) {
            log.warn("EXCLUSION MUTUA banco={} no pudo enviar OK diferido a {} para cuenta={}: {}",
                    properties.getId(), encolada.getBancoId(), encolada.getCuenta(), e.getMessage());
        }
    }

    /**
     * ¿Tengo más prioridad que el solicitante remoto?
     * <ol>
     *   <li>Timestamp menor = mayor prioridad.</li>
     *   <li>Empate de timestamp → se desempata por id de banco (orden lexicográfico menor gana).</li>
     * </ol>
     */
    private boolean tengoMasPrioridad(long miTimestamp, long suTimestamp, String suBancoId) {
        if (miTimestamp != suTimestamp) {
            return miTimestamp < suTimestamp;
        }
        return properties.getId().compareTo(suBancoId) < 0;
    }
}
