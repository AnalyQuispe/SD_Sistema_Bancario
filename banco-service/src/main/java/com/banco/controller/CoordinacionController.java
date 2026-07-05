package com.banco.controller;

import com.banco.coordinator.TwoPhaseCommitParticipant;
import com.banco.model.dto.PrepareRequest;
import com.banco.model.dto.TxRequest;
import com.banco.model.dto.VotoResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints <b>internos</b> del Two-Phase Commit (Hito 2 · Integrante 2).
 *
 * <p>Los llama el coordinador de otro banco a través de {@code BancoRemotoClient}; nunca se
 * exponen al frontend (convención {@code /internal/**}). Delegan en el
 * {@link TwoPhaseCommitParticipant} local.
 */
@RestController
@RequestMapping("/internal/2pc")
@RequiredArgsConstructor
public class CoordinacionController {

    private final TwoPhaseCommitParticipant participante;

    /** PREPARE: valida y reserva; responde el voto YES/NO. */
    @PostMapping("/prepare")
    public VotoResponse prepare(@RequestBody PrepareRequest req) {
        return participante.prepare(req);
    }

    /** COMMIT: aplica la reserva de esa transacción. */
    @PostMapping("/commit")
    public void commit(@RequestBody TxRequest req) {
        participante.commit(req.getTxId());
    }

    /** ABORT: libera la reserva de esa transacción. */
    @PostMapping("/abort")
    public void abort(@RequestBody TxRequest req) {
        participante.abort(req.getTxId());
    }
}
