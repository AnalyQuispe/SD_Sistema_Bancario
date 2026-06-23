package com.banco.service;

import com.banco.config.BancoProperties;
import com.banco.exception.CuentaNoEncontradaException;
import com.banco.exception.SaldoInsuficienteException;
import com.banco.model.BancoData;
import com.banco.model.Cliente;
import com.banco.model.Cuenta;
import com.banco.model.EstadoTransaccion;
import com.banco.model.Transaccion;
import com.banco.model.TipoTransaccion;
import com.banco.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Consultas y operaciones simples (depósito/retiro) sobre las cuentas locales (Hito 1).
 *
 * <p>Cada operación de escritura se realiza dentro del lock de la cuenta afectada
 * ({@link LockManager}) y persiste el resultado vía {@link CuentaRepository}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CuentaService {

    private final CuentaRepository repository;
    private final LockManager lockManager;
    private final BancoProperties properties;

    /** Cuentas de un cliente en ESTE banco. (La vista global entre bancos es Hito 2.) */
    public List<Cuenta> cuentasDeCliente(String clienteId) {
        return buscarCliente(repository.load(), clienteId).getCuentas();
    }

    /** Datos y saldo de una cuenta concreta de este banco. */
    public Cuenta consultarCuenta(String numero) {
        return buscarCuenta(repository.load(), numero);
    }

    /** Depósito en una cuenta. Devuelve la cuenta con el saldo actualizado. */
    public Cuenta depositar(String numero, BigDecimal monto) {
        return lockManager.conLock(numero, () -> {
            BancoData data = repository.load();
            Cuenta cuenta = buscarCuenta(data, numero);
            cuenta.setSaldo(cuenta.getSaldo().add(monto));
            registrar(data, TipoTransaccion.DEPOSITO, null, numero, monto);
            repository.save(data);
            log.info("DEPOSITO {} -> cuenta {} (saldo {})", monto, numero, cuenta.getSaldo());
            return cuenta;
        });
    }

    /** Retiro de una cuenta. Falla si no hay saldo suficiente. */
    public Cuenta retirar(String numero, BigDecimal monto) {
        return lockManager.conLock(numero, () -> {
            BancoData data = repository.load();
            Cuenta cuenta = buscarCuenta(data, numero);
            if (cuenta.getSaldo().compareTo(monto) < 0) {
                throw new SaldoInsuficienteException(numero);
            }
            cuenta.setSaldo(cuenta.getSaldo().subtract(monto));
            registrar(data, TipoTransaccion.RETIRO, numero, null, monto);
            repository.save(data);
            log.info("RETIRO {} <- cuenta {} (saldo {})", monto, numero, cuenta.getSaldo());
            return cuenta;
        });
    }

    // ----------------------------------------------------------------- helpers

    Cliente buscarCliente(BancoData data, String clienteId) {
        return data.getClientes().stream()
                .filter(c -> c.getId().equals(clienteId))
                .findFirst()
                .orElseThrow(() -> new CuentaNoEncontradaException("cliente " + clienteId));
    }

    Cuenta buscarCuenta(BancoData data, String numero) {
        return data.getClientes().stream()
                .flatMap(c -> c.getCuentas().stream())
                .filter(cu -> cu.getNumero().equals(numero))
                .findFirst()
                .orElseThrow(() -> new CuentaNoEncontradaException(numero));
    }

    void registrar(BancoData data, TipoTransaccion tipo, String origen, String destino, BigDecimal monto) {
        data.getTransacciones().add(Transaccion.builder()
                .id("T-" + UUID.randomUUID().toString().substring(0, 8))
                .tipo(tipo)
                .origen(origen)
                .destino(destino)
                .monto(monto)
                .estado(EstadoTransaccion.COMMITTED)
                .timestamp(Instant.now())
                .build());
    }
}
