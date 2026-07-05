package com.banco.coordinator;

import com.banco.config.BancoProperties;
import com.banco.model.EstadoTransaccion;
import com.banco.model.RolParticipante;
import com.banco.model.Voto;
import com.banco.model.dto.PrepareRequest;
import com.banco.model.dto.TxRequest;
import com.banco.model.dto.VotoResponse;
import com.banco.service.BancoRemotoClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lado COORDINADOR del Two-Phase Commit (Hito 2 · Integrante 2).
 *
 * <p>Este banco (el que recibe la petición) orquesta una transferencia entre dos bancos distintos:
 * envía PREPARE al banco de origen (rol DEBITO) y al de destino (rol CREDITO); si <b>ambos</b>
 * votan {@link Voto#YES} decide COMMIT, en caso contrario ABORT, y propaga la decisión a los dos.
 *
 * <p>Despacho local vs. remoto: si el banco participante es este mismo, se llama directamente al
 * {@link TwoPhaseCommitParticipant} (evita una llamada HTTP a sí mismo); si es otro banco, se usa
 * {@link BancoRemotoClient#postInternal} contra sus endpoints {@code /internal/2pc/**}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoPhaseCommitCoordinator {

    private final BancoProperties properties;
    private final BancoRemotoClient remoto;
    private final TwoPhaseCommitParticipant participanteLocal;

    /** Ejecuta el protocolo completo y devuelve el estado final (COMMITTED o ABORTED). */
    public EstadoTransaccion ejecutar(String origen, String destino, BigDecimal monto) {
        String txId = "TX-" + UUID.randomUUID().toString().substring(0, 8);
        String bancoOrigen = remoto.bancoDueno(origen);
        String bancoDestino = remoto.bancoDueno(destino);
        log.info("2PC COORDINADOR tx={} inicia | {} ({}) --{}--> {} ({})",
                txId, origen, bancoOrigen, monto, destino, bancoDestino);

        // -------- Fase 1: PREPARE (voto de cada participante) --------
        Voto votoOrigen = solicitarPrepare(bancoOrigen,
                new PrepareRequest(txId, origen, RolParticipante.DEBITO, monto));
        Voto votoDestino = solicitarPrepare(bancoDestino,
                new PrepareRequest(txId, destino, RolParticipante.CREDITO, monto));

        // -------- Fase 2: decisión y propagación --------
        if (votoOrigen == Voto.YES && votoDestino == Voto.YES) {
            enviarDecision(bancoOrigen, txId, true);
            enviarDecision(bancoDestino, txId, true);
            log.info("2PC COORDINADOR tx={} DECISION=COMMIT", txId);
            return EstadoTransaccion.COMMITTED;
        }

        // Se aborta a ambos: el que votó NO no preparó nada -> su abort es no-op idempotente.
        enviarDecision(bancoOrigen, txId, false);
        enviarDecision(bancoDestino, txId, false);
        log.info("2PC COORDINADOR tx={} DECISION=ABORT (voto origen={}, voto destino={})",
                txId, votoOrigen, votoDestino);
        return EstadoTransaccion.ABORTED;
    }

    private Voto solicitarPrepare(String bancoId, PrepareRequest req) {
        try {
            VotoResponse resp = esLocal(bancoId)
                    ? participanteLocal.prepare(req)
                    : remoto.postInternal(urlDe(bancoId), "/internal/2pc/prepare", req, VotoResponse.class);
            Voto voto = (resp == null || resp.getVoto() == null) ? Voto.NO : resp.getVoto();
            log.info("2PC COORDINADOR tx={} voto de {} = {}", req.getTxId(), bancoId, voto);
            return voto;
        } catch (Exception e) {
            log.warn("2PC COORDINADOR tx={} PREPARE a {} falló: {} -> se cuenta como voto NO",
                    req.getTxId(), bancoId, e.getMessage());
            return Voto.NO;
        }
    }

    private void enviarDecision(String bancoId, String txId, boolean commit) {
        String path = commit ? "/internal/2pc/commit" : "/internal/2pc/abort";
        try {
            if (esLocal(bancoId)) {
                if (commit) {
                    participanteLocal.commit(txId);
                } else {
                    participanteLocal.abort(txId);
                }
            } else {
                remoto.postInternal(urlDe(bancoId), path, new TxRequest(txId), Void.class);
            }
        } catch (Exception e) {
            // La decisión no debe perderse: se registra para reintento (Integrante 4).
            log.error("2PC COORDINADOR tx={} decisión {} hacia {} falló: {} (pendiente de reintento)",
                    txId, commit ? "COMMIT" : "ABORT", bancoId, e.getMessage());
        }
    }

    private boolean esLocal(String bancoId) {
        return properties.getId().equals(bancoId);
    }

    private String urlDe(String bancoId) {
        return remoto.urlDePeer(bancoId)
                .orElseThrow(() -> new IllegalStateException("Peer no configurado: " + bancoId));
    }
}
