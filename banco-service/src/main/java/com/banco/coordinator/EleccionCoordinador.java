package com.banco.coordinator;

import com.banco.config.BancoProperties;
import com.banco.config.BancoProperties.Peer;
import com.banco.model.dto.EleccionMessage;
import com.banco.service.BancoRemotoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Algoritmo de elección de coordinador <b>Bully</b> (Hito 2 · Integrante 3).
 *
 * <p>Cada banco tiene una prioridad numérica ({@code banco.prioridad} en YAML):
 * A=1, B=2, C=3.  El nodo con la prioridad <b>más alta</b> que esté vivo gana.
 *
 * <h3>Flujo del algoritmo Bully</h3>
 * <ol>
 *   <li>El nodo que detecta la necesidad de un coordinador envía {@code ELECCION}
 *       a todos los peers de prioridad <b>mayor</b>.</li>
 *   <li>Si alguno responde {@code OK}, ese peer toma el control y el nodo que
 *       inició espera el anuncio {@code COORDINADOR}.</li>
 *   <li>Si <b>nadie mayor</b> responde (caídos o inexistentes), el nodo se
 *       proclama coordinador y envía {@code COORDINADOR} a todos.</li>
 * </ol>
 *
 * <p>Cada paso se loguea con {@code @Slf4j} para que sirva de evidencia en el informe.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EleccionCoordinador {

    private final BancoProperties properties;
    private final BancoRemotoClient remoto;

    /** ID del coordinador vigente ({@code null} = desconocido / pendiente de elección). */
    private volatile String coordinadorActual;

    // ------------------------------------------------------------ API pública

    /**
     * Inicia una elección Bully y devuelve el id del coordinador resultante.
     *
     * <p>Se invoca:
     * <ul>
     *   <li>Al arrancar la aplicación ({@link #eleccionInicial}).</li>
     *   <li>Cuando otro componente detecta que el coordinador actual no responde.</li>
     *   <li>Manualmente vía el endpoint {@code POST /internal/eleccion/forzar}.</li>
     * </ul>
     */
    public synchronized String iniciarEleccion() {
        log.info("ELECCION banco={} inicia elección (prioridad {})",
                properties.getId(), properties.getPrioridad());

        // Enviar ELECCION a los peers con prioridad MAYOR
        List<Peer> mayores = properties.getPeers().stream()
                .filter(this::tienePrioridadMayor)
                .toList();

        if (mayores.isEmpty()) {
            // Soy el de mayor prioridad → me proclamo coordinador directamente
            proclamarCoordinador();
            return coordinadorActual;
        }

        boolean alguienRespondio = false;
        for (Peer peer : mayores) {
            try {
                EleccionMessage msg = new EleccionMessage(
                        properties.getId(), properties.getPrioridad(), "ELECCION");
                log.info("ELECCION banco={} envía ELECCION a {} (prioridad mayor)",
                        properties.getId(), peer.getId());

                remoto.postInternal(peer.getUrl(), "/internal/eleccion", msg, Map.class);
                // Si responde sin error → ese peer está vivo y tiene mayor prioridad
                log.info("ELECCION banco={} recibió OK de {} → ese peer toma el control",
                        properties.getId(), peer.getId());
                alguienRespondio = true;
            } catch (Exception e) {
                log.info("ELECCION banco={} peer {} no respondió (caído o inalcanzable): {}",
                        properties.getId(), peer.getId(), e.getMessage());
            }
        }

        if (!alguienRespondio) {
            // Ningún peer mayor respondió → me proclamo coordinador
            proclamarCoordinador();
        }
        // Si alguien respondió, ese peer hará su propia elección y me notificará
        // como COORDINADOR; mientras tanto, esperamos.
        return coordinadorActual;
    }

    /**
     * Recibe un mensaje ELECCION de un nodo con menor prioridad.
     * Responde OK (indicando «yo estoy vivo y tengo mayor prioridad»),
     * y luego inicia su propia elección para verificar si hay alguien aún mayor.
     */
    public Map<String, String> recibirEleccion(EleccionMessage msg) {
        log.info("ELECCION banco={} recibe ELECCION de {} (prioridad {}) → responde OK",
                properties.getId(), msg.getBancoId(), msg.getPrioridad());
        // Disparar mi propia elección en segundo plano
        new Thread(this::iniciarEleccion, "eleccion-" + properties.getId()).start();
        return Map.of("status", "OK", "banco", properties.getId());
    }

    /**
     * Recibe el anuncio {@code COORDINADOR} del nodo que ganó la elección.
     */
    public void recibirCoordinador(EleccionMessage msg) {
        String anterior = coordinadorActual;
        coordinadorActual = msg.getBancoId();
        log.info("ELECCION banco={} recibe anuncio: nuevo coordinador = {} (anterior: {})",
                properties.getId(), msg.getBancoId(), anterior);
    }

    /** Devuelve el coordinador actual conocido por este nodo. */
    public String getCoordinadorActual() {
        return coordinadorActual;
    }

    // ------------------------------------------------------------ arranque

    /** Ejecuta una elección al arrancar la aplicación para establecer el coordinador inicial. */
    @EventListener(ApplicationReadyEvent.class)
    public void eleccionInicial() {
        log.info("ELECCION banco={} ejecutando elección inicial al arrancar...", properties.getId());
        iniciarEleccion();
    }

    // ------------------------------------------------------------ internos

    /**
     * Se proclama coordinador y lo anuncia a todos los peers.
     */
    private void proclamarCoordinador() {
        coordinadorActual = properties.getId();
        log.info("ELECCION banco={} se proclama COORDINADOR (prioridad {}, nadie mayor respondió)",
                properties.getId(), properties.getPrioridad());

        EleccionMessage anuncio = new EleccionMessage(
                properties.getId(), properties.getPrioridad(), "COORDINADOR");

        for (Peer peer : properties.getPeers()) {
            try {
                remoto.postInternal(peer.getUrl(), "/internal/coordinador", anuncio, Void.class);
                log.info("ELECCION banco={} envía COORDINADOR a {}", properties.getId(), peer.getId());
            } catch (Exception e) {
                log.warn("ELECCION banco={} no pudo notificar a {} (posiblemente caído): {}",
                        properties.getId(), peer.getId(), e.getMessage());
            }
        }
    }

    /**
     * Determina si un peer tiene prioridad mayor que este nodo.
     * En la convención del Bully, se usa el campo {@code prioridad} de cada peer
     * para comparar; pero como los peers no exponen su prioridad en la configuración
     * local, se usa la convención del id: BANCO_A=1, BANCO_B=2, BANCO_C=3.
     */
    private boolean tienePrioridadMayor(Peer peer) {
        int prioridadPeer = prioridadDe(peer.getId());
        return prioridadPeer > properties.getPrioridad();
    }

    /**
     * Convención de prioridad por id de banco: A=1, B=2, C=3.
     * Esto coincide con la configuración YAML y permite determinar la prioridad
     * de un peer sin necesidad de consultarlo por red.
     */
    private int prioridadDe(String bancoId) {
        // BANCO_A → 'A' → 1, BANCO_B → 'B' → 2, BANCO_C → 'C' → 3
        char letra = bancoId.charAt(bancoId.length() - 1);
        return letra - 'A' + 1;
    }
}
