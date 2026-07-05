package com.banco.controller;

import com.banco.model.BancoData;
import com.banco.service.ReplicacionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

/**
 * Controlador de replicación y recuperación (Hito 2 · Integrante 4).
 * Expone endpoints internos (/internal/replica/**) para el intercambio de datos entre peers.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class ReplicacionController {

    private final ReplicacionService replicacionService;

    /** Recibe la réplica enviada por otro banco. */
    @PostMapping("/internal/replicar")
    public void replicar(@RequestBody BancoData data) {
        replicacionService.aplicarReplica(data);
    }

    /** Devuelve el estado de versiones de todas las réplicas almacenadas localmente. */
    @GetMapping("/internal/replica/estado")
    public Map<String, Long> obtenerEstadoReplicas() {
        return replicacionService.obtenerEstadoReplicas();
    }

    /** Devuelve la réplica de un banco específico, o 404 si no existe. */
    @GetMapping("/internal/replica/{bancoId}")
    public BancoData obtenerReplica(@PathVariable String bancoId) {
        return replicacionService.obtenerReplica(bancoId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Réplica no encontrada para " + bancoId));
    }

    /** Endpoint administrativo para forzar la restauración local desde los peers. */
    @PostMapping("/internal/replica/restaurar")
    public Map<String, String> restaurar() {
        try {
            String mensaje = replicacionService.restaurarDesdeReplicas();
            return Map.of("status", "SUCCESS", "message", mensaje);
        } catch (Exception e) {
            log.error("Fallo al restaurar el banco desde réplicas: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error al restaurar: " + e.getMessage());
        }
    }
}
