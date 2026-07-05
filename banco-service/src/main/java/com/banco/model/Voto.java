package com.banco.model;

/**
 * Voto de un participante en la fase PREPARE del Two-Phase Commit.
 *
 * <p>{@link #YES} = "estoy listo para confirmar" (fondos reservados / cuenta válida);
 * {@link #NO} = "no puedo" (saldo insuficiente, cuenta inexistente o fallo). El coordinador
 * confirma solo si <b>todos</b> votan {@link #YES}; en caso contrario, aborta.
 */
public enum Voto {
    YES,
    NO
}
