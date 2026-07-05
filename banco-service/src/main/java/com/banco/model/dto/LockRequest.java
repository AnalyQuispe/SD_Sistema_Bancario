package com.banco.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mensaje REQUEST / OK del algoritmo Ricart–Agrawala (Hito 2 · Integrante 3).
 *
 * <p>Un nodo envía un {@code LockRequest} a todos los peers para solicitar
 * acceso exclusivo a una cuenta compartida. Cada peer responde con un
 * {@link LockResponse} (OK inmediato o diferido según su propio estado).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LockRequest {

    /** Banco que solicita (o que responde OK), p. ej. {@code BANCO_A}. */
    private String bancoId;

    /** Cuenta sobre la que se pide el lock, p. ej. {@code A-2001}. */
    private String cuenta;

    /** Timestamp de Lamport en el momento de la solicitud. */
    private long timestamp;
}
