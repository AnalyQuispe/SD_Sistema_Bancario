package com.banco.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Raíz del archivo {@code cuentas-bancoX.json}. Es la unidad que el
 * {@code CuentaRepository} lee y escribe en disco de forma atómica.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BancoData {

    private String bancoId;

    @Builder.Default
    private List<Cliente> clientes = new ArrayList<>();

    @Builder.Default
    private List<Transaccion> transacciones = new ArrayList<>();
}
