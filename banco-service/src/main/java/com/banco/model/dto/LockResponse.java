package com.banco.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta a un {@link LockRequest} en el algoritmo Ricart–Agrawala
 * (Hito 2 · Integrante 3).
 *
 * <p>Si {@code ok = true}, el peer concede el acceso (no está interesado en
 * la misma cuenta o tiene un timestamp mayor). Si {@code ok = false}, el
 * peer difiere la respuesta (se la enviará cuando libere su sección crítica).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockResponse {

    /** Banco que responde. */
    private String bancoId;

    /** Cuenta sobre la que se responde. */
    private String cuenta;

    /** {@code true} = OK, puedes proceder; {@code false} = diferido. */
    private boolean ok;
}
