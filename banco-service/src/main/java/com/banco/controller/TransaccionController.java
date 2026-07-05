package com.banco.controller;

import com.banco.model.Transaccion;
import com.banco.repository.CuentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * Historial de transacciones de ESTE banco (Hito 2 · Integrante 5).
 *
 * <p>Lo consume el frontend para mostrar el historial con su estado
 * ({@code COMMITTED}/{@code ABORTED}); consultando a los 3 bancos vía el
 * API Gateway se obtiene el historial global.
 */
@RestController
@RequestMapping("/api/transacciones")
@RequiredArgsConstructor
public class TransaccionController {

    private final CuentaRepository repository;

    /** Transacciones registradas en este banco, las más recientes primero. */
    @GetMapping
    public List<Transaccion> historial() {
        return repository.load().getTransacciones().stream()
                .sorted(Comparator.comparing(Transaccion::getTimestamp,
                        Comparator.nullsLast(Comparator.<Instant>naturalOrder().reversed())))
                .toList();
    }
}
