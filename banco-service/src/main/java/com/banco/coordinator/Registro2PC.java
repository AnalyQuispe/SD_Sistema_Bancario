package com.banco.coordinator;

import com.banco.model.RolParticipante;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registro en memoria de las reservas PREPARADAS pendientes de este banco (participante 2PC).
 *
 * <p>Entre la fase PREPARE y la decisión (COMMIT/ABORT) el participante necesita recordar
 * qué reservó por cada {@code txId} para poder aplicarla o revertirla. Se mantiene en memoria
 * (la evidencia persistente vive en el archivo como una {@code Transaccion} en estado
 * {@code PREPARED}); el mapa permite además que COMMIT/ABORT sean <b>idempotentes</b>:
 * si el {@code txId} ya no está, la operación es un no-op (tolera reintentos del coordinador).
 */
@Component
public class Registro2PC {

    /** Lo que un participante reservó para una transacción concreta. */
    public record ReservaPendiente(String cuenta, RolParticipante rol, BigDecimal monto) {}

    private final Map<String, ReservaPendiente> pendientes = new ConcurrentHashMap<>();

    public void registrar(String txId, ReservaPendiente reserva) {
        pendientes.put(txId, reserva);
    }

    public Optional<ReservaPendiente> obtener(String txId) {
        return Optional.ofNullable(pendientes.get(txId));
    }

    public Optional<ReservaPendiente> eliminar(String txId) {
        return Optional.ofNullable(pendientes.remove(txId));
    }
}
