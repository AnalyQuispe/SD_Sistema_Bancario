package com.banco.controller;

import com.banco.exception.CuentaNoEncontradaException;
import com.banco.model.Cuenta;
import com.banco.service.BancoRemotoClient;
import com.banco.service.CuentaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

/**
 * Endpoints públicos de consulta de cuentas.
 *
 * <p>Desde el Hito 2 la consulta de cuentas de un cliente ofrece la <b>vista global</b>:
 * une las cuentas de este banco con las de los otros dos (Integrante 1), de modo que el
 * cliente ve todas sus cuentas sin importar a qué banco pregunte.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CuentaController {

    private final CuentaService cuentaService;
    private final BancoRemotoClient bancoRemotoClient;

    /**
     * Todas las cuentas del cliente en los 3 bancos (local + peers).
     * Responde 404 solo si el cliente no tiene cuentas en ningún banco.
     */
    @GetMapping("/clientes/{id}/cuentas")
    public List<Cuenta> cuentasDeCliente(@PathVariable String id) {
        List<Cuenta> locales = cuentaService.cuentasLocalesDeCliente(id);
        List<Cuenta> remotas = bancoRemotoClient.cuentasDeClienteEnPeers(id);

        List<Cuenta> todas = new ArrayList<>(locales);
        todas.addAll(remotas);

        log.info("Vista global del cliente {}: {} local(es) + {} remota(s) = {} cuenta(s)",
                id, locales.size(), remotas.size(), todas.size());

        if (todas.isEmpty()) {
            throw new CuentaNoEncontradaException("cliente " + id);
        }
        return todas;
    }

    @GetMapping("/cuentas/{numero}")
    public Cuenta consultarCuenta(@PathVariable String numero) {
        return cuentaService.consultarCuenta(numero);
    }
}
