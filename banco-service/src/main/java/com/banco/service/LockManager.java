package com.banco.service;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Control de concurrencia local: un {@link ReentrantLock} por número de cuenta.
 *
 * <p>Garantiza que dos operaciones simultáneas sobre la MISMA cuenta se serialicen
 * (criterio de aceptación del Hito 1) sin bloquear operaciones sobre cuentas distintas.
 *
 * <p>Para operaciones que tocan varias cuentas (transferencia local), los locks se
 * adquieren SIEMPRE en orden alfabético del número de cuenta para evitar interbloqueos.
 */
@Component
public class LockManager {

    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    private ReentrantLock lockFor(String cuenta) {
        return locks.computeIfAbsent(cuenta, k -> new ReentrantLock(true));
    }

    /** Ejecuta {@code accion} en exclusión mutua sobre una sola cuenta. */
    public <T> T conLock(String cuenta, Supplier<T> accion) {
        ReentrantLock lock = lockFor(cuenta);
        lock.lock();
        try {
            return accion.get();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ejecuta {@code accion} en exclusión mutua sobre varias cuentas, tomando los locks
     * en orden estable para prevenir deadlocks.
     */
    public <T> T conLocks(List<String> cuentas, Supplier<T> accion) {
        List<ReentrantLock> ordenados = cuentas.stream()
                .distinct()
                .sorted()
                .map(this::lockFor)
                .collect(Collectors.toList());
        ordenados.forEach(ReentrantLock::lock);
        try {
            return accion.get();
        } finally {
            // Liberar en orden inverso.
            for (int i = ordenados.size() - 1; i >= 0; i--) {
                ordenados.get(i).unlock();
            }
        }
    }
}
