package com.banco.controller;

import com.banco.model.Cuenta;
import com.banco.model.EstadoTransaccion;
import com.banco.model.dto.OperacionRequest;
import com.banco.model.dto.TransferenciaRequest;
import com.banco.service.CuentaService;
import com.banco.service.TransferenciaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Operaciones de depósito, retiro y transferencia local (Hito 1). */
@RestController
@RequestMapping("/api/operaciones")
@RequiredArgsConstructor
public class OperacionController {

    private final CuentaService cuentaService;
    private final TransferenciaService transferenciaService;

    @PostMapping("/deposito")
    public Cuenta deposito(@Valid @RequestBody OperacionRequest req) {
        return cuentaService.depositar(req.getCuenta(), req.getMonto());
    }

    @PostMapping("/retiro")
    public Cuenta retiro(@Valid @RequestBody OperacionRequest req) {
        return cuentaService.retirar(req.getCuenta(), req.getMonto());
    }

    /**
     * Transferencia local o distribuida (2PC). El servicio decide la ruta según los bancos
     * dueños de origen y destino; aquí solo se traduce el estado final a la respuesta HTTP.
     */
    @PostMapping("/transferencia")
    public ResponseEntity<Map<String, String>> transferencia(@Valid @RequestBody TransferenciaRequest req) {
        EstadoTransaccion estado = transferenciaService.transferencia(
                req.getCuentaOrigen(), req.getCuentaDestino(), req.getMonto());
        String mensaje = estado == EstadoTransaccion.COMMITTED
                ? "Transferencia realizada"
                : "Transferencia abortada (algún participante no pudo confirmar)";
        return ResponseEntity.ok(Map.of("estado", estado.name(), "mensaje", mensaje));
    }
}
