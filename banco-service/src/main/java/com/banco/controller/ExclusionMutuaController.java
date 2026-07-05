package com.banco.controller;

import com.banco.coordinator.ExclusionMutua;
import com.banco.model.dto.LockRequest;
import com.banco.model.dto.LockResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints <b>internos</b> de exclusión mutua distribuida — Ricart–Agrawala
 * (Hito 2 · Integrante 3).
 *
 * <p>Los llama otro banco para solicitar o conceder acceso a una cuenta compartida.
 * Nunca se exponen al frontend (convención {@code /internal/**}, protegidos por
 * {@link com.banco.config.InternalAuthFilter}).
 */
@RestController
@RequestMapping("/internal/lock")
@RequiredArgsConstructor
public class ExclusionMutuaController {

    private final ExclusionMutua exclusionMutua;

    /**
     * REQUEST: otro nodo solicita acceso exclusivo a una cuenta.
     * Responde OK inmediato si no hay conflicto, o un diferido (ok=false) que se
     * resolverá después vía {@code /release}.
     */
    @PostMapping("/request")
    public LockResponse recibirRequest(@RequestBody LockRequest req) {
        return exclusionMutua.recibirRequest(req);
    }

    /**
     * RELEASE (OK diferido): otro nodo nos concede el acceso que habíamos solicitado.
     * Decrementa el latch interno para que {@code adquirir()} pueda continuar.
     */
    @PostMapping("/release")
    public void recibirRelease(@RequestBody LockRequest req) {
        exclusionMutua.recibirRelease(req);
    }
}
