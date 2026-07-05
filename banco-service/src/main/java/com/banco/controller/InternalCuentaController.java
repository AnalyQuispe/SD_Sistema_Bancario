package com.banco.controller;

import com.banco.model.Cuenta;
import com.banco.service.CuentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints <b>internos</b> banco-a-banco (Hito 2, Integrante 1).
 *
 * <p>Solo los llaman los otros bancos a través de {@code BancoRemotoClient}; el API Gateway
 * nunca los expone al frontend (convención {@code /internal/**}).
 *
 * <p>Clave para evitar recursión infinita: estos endpoints responden únicamente con datos
 * <b>locales</b> de este banco y no vuelven a consultar a los peers.
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCuentaController {

    private final CuentaService cuentaService;

    /** Cuentas del cliente SOLO en este banco (o lista vacía si no tiene ninguna aquí). */
    @GetMapping("/clientes/{id}/cuentas-locales")
    public List<Cuenta> cuentasLocales(@PathVariable String id) {
        return cuentaService.cuentasLocalesDeCliente(id);
    }

    /** Una cuenta concreta de este banco. Responde 404 si no existe aquí. */
    @GetMapping("/cuentas/{numero}")
    public Cuenta cuentaLocal(@PathVariable String numero) {
        return cuentaService.consultarCuenta(numero);
    }
}
