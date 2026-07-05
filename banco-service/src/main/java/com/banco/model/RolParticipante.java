package com.banco.model;

/**
 * Rol de un participante en una transferencia distribuida (2PC, Hito 2 · Integrante 2).
 *
 * <p>El banco dueño de la cuenta ORIGEN participa como {@link #DEBITO} (le restan);
 * el banco dueño de la cuenta DESTINO participa como {@link #CREDITO} (le suman).
 */
public enum RolParticipante {
    DEBITO,
    CREDITO
}
