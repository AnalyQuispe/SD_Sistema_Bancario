package com.banco.coordinator;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Reloj lógico de Lamport (Hito 2 · Integrante 3).
 *
 * <p>Ordena eventos en el sistema distribuido sin depender de relojes físicos.
 * Lo usa {@link ExclusionMutua} para ordenar las peticiones de lock según
 * el algoritmo Ricart–Agrawala: el nodo con timestamp menor tiene prioridad.
 *
 * <p>Regla de Lamport: al recibir un mensaje con timestamp {@code remoto},
 * el reloj local se actualiza a {@code max(local, remoto) + 1}.
 */
@Component
public class RelojLamport {

    private final AtomicLong tick = new AtomicLong(0);

    /**
     * Evento local: incrementa y devuelve el nuevo timestamp.
     * Se invoca antes de enviar un REQUEST o de tomar una decisión local.
     */
    public long tick() {
        return tick.incrementAndGet();
    }

    /**
     * Evento de recepción: actualiza el reloj al máximo entre el local y el
     * remoto, e incrementa en 1. Garantiza causalidad: el evento de recepción
     * siempre tiene un timestamp mayor que el de envío.
     *
     * @param remoto timestamp del mensaje recibido
     * @return el nuevo valor del reloj local
     */
    public long actualizar(long remoto) {
        return tick.updateAndGet(local -> Math.max(local, remoto) + 1);
    }

    /** Valor actual del reloj sin modificarlo (para consultas de estado). */
    public long valor() {
        return tick.get();
    }
}
