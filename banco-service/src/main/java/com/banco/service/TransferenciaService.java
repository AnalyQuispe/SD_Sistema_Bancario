package com.banco.service;

import com.banco.config.BancoProperties;
import com.banco.coordinator.TwoPhaseCommitCoordinator;
import com.banco.exception.SaldoInsuficienteException;
import com.banco.model.BancoData;
import com.banco.model.Cuenta;
import com.banco.model.EstadoTransaccion;
import com.banco.model.TipoTransaccion;
import com.banco.model.dto.TransferenciaRequest;
import com.banco.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final BancoProperties properties;
    private final BancoRemotoClient bancoRemotoClient;
    private final TwoPhaseCommitCoordinator coordinator;

    /**
     * Punto de entrada único de una transferencia. Decide la ruta según dónde vivan las cuentas:
     * <ul>
     *   <li><b>Ambas en este banco</b> → transferencia local atómica (Hito 1).</li>
     *   <li><b>Ambas en otro mismo banco</b> → se reenvía a ese banco (evita un 2PC innecesario
     *       sobre un solo archivo).</li>
     *   <li><b>Bancos distintos</b> → Two-Phase Commit, con este banco como coordinador (Hito 2).</li>
     * </ul>
     *
     * @return el estado final: {@code COMMITTED} o {@code ABORTED}.
     */
    public EstadoTransaccion transferencia(String origen, String destino, BigDecimal monto) {
        if (origen.equals(destino)) {
            throw new IllegalArgumentException("La cuenta origen y destino no pueden ser la misma");
        }
        String bancoOrigen = bancoRemotoClient.bancoDueno(origen);
        String bancoDestino = bancoRemotoClient.bancoDueno(destino);
        boolean origenLocal = bancoOrigen.equals(properties.getId());
        boolean destinoLocal = bancoDestino.equals(properties.getId());

        if (origenLocal && destinoLocal) {
            transferenciaLocal(origen, destino, monto);
            return EstadoTransaccion.COMMITTED;
        }
        if (bancoOrigen.equals(bancoDestino)) {
            return reenviarADueno(bancoOrigen, origen, destino, monto);
        }
        return coordinator.ejecutar(origen, destino, monto);
    }

    /**
     * Reenvía una transferencia intra-banco (origen y destino en el mismo banco remoto) a ese
     * banco, que la resolverá como local. Devuelve {@code COMMITTED} si el reenvío tuvo éxito.
     */
    private EstadoTransaccion reenviarADueno(String bancoId, String origen, String destino, BigDecimal monto) {
        String url = bancoRemotoClient.urlDePeer(bancoId)
                .orElseThrow(() -> new IllegalStateException("Peer no configurado: " + bancoId));
        TransferenciaRequest req = new TransferenciaRequest();
        req.setCuentaOrigen(origen);
        req.setCuentaDestino(destino);
        req.setMonto(monto);
        try {
            bancoRemotoClient.postInternal(url, "/api/operaciones/transferencia", req, Void.class);
            log.info("TRANSFERENCIA intra-banco {} -> {} reenviada a {} -> COMMITTED", origen, destino, bancoId);
            return EstadoTransaccion.COMMITTED;
        } catch (Exception e) {
            log.warn("Reenvío de transferencia a {} falló: {} -> ABORTED", bancoId, e.getMessage());
            return EstadoTransaccion.ABORTED;
        }
    }

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
     * Busca una cuenta que, por el enrutado de {@link #transferencia}, se garantiza local.
     * Si no existe, propaga {@link CuentaNoEncontradaException} (HTTP 404).
     */
    private Cuenta buscarLocal(BancoData data, String numero) {
        return cuentaService.buscarCuenta(data, numero);
    }
}
