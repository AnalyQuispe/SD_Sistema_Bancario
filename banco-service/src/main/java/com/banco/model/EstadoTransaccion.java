package com.banco.model;

/**
 * Estados del ciclo de vida de una transacción. Los estados {@code PREPARED} y
 * {@code ABORTED} son clave para el Two-Phase Commit del Hito 2.
 */
public enum EstadoTransaccion {
    PENDING,
    PREPARED,
    COMMITTED,
    ABORTED
}
