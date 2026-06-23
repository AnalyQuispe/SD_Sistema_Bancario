package com.banco.controller;

import com.banco.model.Cuenta;
import com.banco.service.CuentaService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Endpoints de consulta de cuentas (Hito 1, alcance local a este banco). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;

    @GetMapping("/clientes/{id}/cuentas")
    public List<Cuenta> cuentasDeCliente(@PathVariable String id) {
        return cuentaService.cuentasDeCliente(id);
    }

    @GetMapping("/cuentas/{numero}")
    public Cuenta consultarCuenta(@PathVariable String numero) {
        return cuentaService.consultarCuenta(numero);
    }
}
