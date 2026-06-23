package com.banco.service;

import com.banco.exception.CuentaNoEncontradaException;
import com.banco.exception.SaldoInsuficienteException;
import com.banco.model.BancoData;
import com.banco.model.Cuenta;
import com.banco.model.TipoTransaccion;
import com.banco.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

/**
 * Transferencias entre cuentas del MISMO banco (Hito 1).
 *
 * <p>La operación es atómica: retiro del origen + depósito en el destino se aplican y
 * se persisten en una sola escritura, bajo los locks de ambas cuentas. Esto cubre el
 * requisito de "operaciones múltiples" de la consigna.
 *
 * <p>Las transferencias hacia otro banco (origen y destino en bancos distintos) se
 * resolverán con Two-Phase Commit en el Hito 2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferenciaService {

    private final CuentaRepository repository;
    private final CuentaService cuentaService;
    private final LockManager lockManager;

    public void transferenciaLocal(String origen, String destino, BigDecimal monto) {
        if (origen.equals(destino)) {
            throw new IllegalArgumentException("La cuenta origen y destino no pueden ser la misma");
        }

        lockManager.conLocks(List.of(origen, destino), () -> {
            BancoData data = repository.load();

            Cuenta cuentaOrigen = buscarLocal(data, origen);
            Cuenta cuentaDestino = buscarLocal(data, destino);

            if (cuentaOrigen.getSaldo().compareTo(monto) < 0) {
                throw new SaldoInsuficienteException(origen);
            }

            cuentaOrigen.setSaldo(cuentaOrigen.getSaldo().subtract(monto));
            cuentaDestino.setSaldo(cuentaDestino.getSaldo().add(monto));
            cuentaService.registrar(data, TipoTransaccion.TRANSFERENCIA, origen, destino, monto);

            repository.save(data);
            log.info("TRANSFERENCIA local {} : {} -> {} (saldos {} / {})",
                    monto, origen, destino, cuentaOrigen.getSaldo(), cuentaDestino.getSaldo());
            return null;
        });
    }

    /**
     * Busca una cuenta que debe ser local. Si no existe en este banco, asume que es una
     * cuenta de otro banco: eso es una transferencia distribuida (Hito 2).
     */
    private Cuenta buscarLocal(BancoData data, String numero) {
        try {
            return cuentaService.buscarCuenta(data, numero);
        } catch (CuentaNoEncontradaException e) {
            throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "La cuenta " + numero + " no es de este banco; las transferencias "
                            + "distribuidas se implementan en el Hito 2 (2PC).");
        }
    }
}
