package com.banco.coordinator;

import com.banco.coordinator.Registro2PC.ReservaPendiente;
import com.banco.exception.CuentaNoEncontradaException;
import com.banco.model.BancoData;
import com.banco.model.Cuenta;
import com.banco.model.EstadoTransaccion;
import com.banco.model.RolParticipante;
import com.banco.model.TipoTransaccion;
import com.banco.model.Transaccion;
import com.banco.model.dto.PrepareRequest;
import com.banco.model.dto.VotoResponse;
import com.banco.repository.CuentaRepository;
import com.banco.service.CuentaService;
import com.banco.service.LockManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Lado PARTICIPANTE del Two-Phase Commit (Hito 2 · Integrante 2).
 *
 * <p>Aplica las tres fases sobre las cuentas <b>locales</b> de este banco. Modelo de reserva:
 * <b>débito tentativo</b>. En PREPARE el origen resta ya el importe (queda "reservado"), de modo
 * que cualquier operación local concurrente ve el saldo reducido sin lógica extra; el destino solo
 * verifica que la cuenta existe. En COMMIT el destino suma; en ABORT el origen devuelve el importe.
 *
 * <p>Cada fase se ejecuta bajo el {@link LockManager} de la cuenta para serializar 2PC concurrentes
 * sobre la misma cuenta, persiste el estado ({@code PREPARED} → {@code COMMITTED}/{@code ABORTED})
 * como {@link Transaccion} en el archivo (evidencia para el informe) y se loguea con {@code @Slf4j}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoPhaseCommitParticipant {

    private final CuentaRepository repository;
    private final LockManager lockManager;
    private final CuentaService cuentaService;
    private final Registro2PC registro;

    /** Fase PREPARE: valida y reserva. Devuelve el voto YES/NO. */
    public VotoResponse prepare(PrepareRequest req) {
        return lockManager.conLock(req.getCuenta(), () -> {
            BancoData data = repository.load();

            Cuenta cuenta;
            try {
                cuenta = cuentaService.buscarCuenta(data, req.getCuenta());
            } catch (CuentaNoEncontradaException e) {
                log.warn("2PC PREPARE tx={} rol={} cuenta={} -> NO (cuenta inexistente en este banco)",
                        req.getTxId(), req.getRol(), req.getCuenta());
                return VotoResponse.no(req.getTxId(), "cuenta inexistente");
            }

            if (req.getRol() == RolParticipante.DEBITO) {
                if (cuenta.getSaldo().compareTo(req.getMonto()) < 0) {
                    log.warn("2PC PREPARE tx={} DEBITO cuenta={} monto={} -> NO (saldo insuficiente, saldo={})",
                            req.getTxId(), req.getCuenta(), req.getMonto(), cuenta.getSaldo());
                    return VotoResponse.no(req.getTxId(), "saldo insuficiente");
                }
                // Débito tentativo: se resta ya; se devolverá en ABORT si la tx no prospera.
                cuenta.setSaldo(cuenta.getSaldo().subtract(req.getMonto()));
            }

            data.getTransacciones().add(Transaccion.builder()
                    .id(req.getTxId())
                    .tipo(TipoTransaccion.TRANSFERENCIA)
                    .origen(req.getRol() == RolParticipante.DEBITO ? req.getCuenta() : null)
                    .destino(req.getRol() == RolParticipante.CREDITO ? req.getCuenta() : null)
                    .monto(req.getMonto())
                    .estado(EstadoTransaccion.PREPARED)
                    .timestamp(Instant.now())
                    .build());
            repository.save(data);
            registro.registrar(req.getTxId(),
                    new ReservaPendiente(req.getCuenta(), req.getRol(), req.getMonto()));

            log.info("2PC PREPARE tx={} rol={} cuenta={} monto={} -> YES (saldo tras reserva: {})",
                    req.getTxId(), req.getRol(), req.getCuenta(), req.getMonto(), cuenta.getSaldo());
            return VotoResponse.yes(req.getTxId());
        });
    }

    /** Fase COMMIT: aplica definitivamente la reserva. Idempotente. */
    public void commit(String txId) {
        var opt = registro.obtener(txId);
        if (opt.isEmpty()) {
            log.info("2PC COMMIT tx={} -> no-op (sin reserva pendiente; ya aplicado o no preparado aquí)", txId);
            return;
        }
        ReservaPendiente reserva = opt.get();
        lockManager.conLock(reserva.cuenta(), () -> {
            BancoData data = repository.load();
            if (reserva.rol() == RolParticipante.CREDITO) {
                Cuenta cuenta = cuentaService.buscarCuenta(data, reserva.cuenta());
                cuenta.setSaldo(cuenta.getSaldo().add(reserva.monto()));
            }
            marcarEstado(data, txId, EstadoTransaccion.COMMITTED);
            repository.save(data);
            registro.eliminar(txId);
            log.info("2PC COMMIT tx={} rol={} cuenta={} -> COMMITTED", txId, reserva.rol(), reserva.cuenta());
            // Nota (Integrante 4): La replicación se dispara automáticamente al guardar vía BancoDataChangedEvent.
            return null;
        });
    }

    /** Fase ABORT: revierte la reserva sin tocar el saldo definitivo. Idempotente. */
    public void abort(String txId) {
        var opt = registro.obtener(txId);
        if (opt.isEmpty()) {
            log.info("2PC ABORT tx={} -> no-op (sin reserva pendiente)", txId);
            return;
        }
        ReservaPendiente reserva = opt.get();
        lockManager.conLock(reserva.cuenta(), () -> {
            BancoData data = repository.load();
            if (reserva.rol() == RolParticipante.DEBITO) {
                // Devolver el importe reservado en PREPARE.
                Cuenta cuenta = cuentaService.buscarCuenta(data, reserva.cuenta());
                cuenta.setSaldo(cuenta.getSaldo().add(reserva.monto()));
            }
            marcarEstado(data, txId, EstadoTransaccion.ABORTED);
            repository.save(data);
            registro.eliminar(txId);
            log.info("2PC ABORT tx={} rol={} cuenta={} -> ABORTED (reserva liberada)",
                    txId, reserva.rol(), reserva.cuenta());
            return null;
        });
    }

    private void marcarEstado(BancoData data, String txId, EstadoTransaccion estado) {
        data.getTransacciones().stream()
                .filter(t -> txId.equals(t.getId()))
                .forEach(t -> t.setEstado(estado));
    }
}
