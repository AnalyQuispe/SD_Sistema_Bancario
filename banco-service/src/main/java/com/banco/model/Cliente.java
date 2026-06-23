package com.banco.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Cliente del banco. Un mismo {@code id} de cliente puede aparecer en los archivos
 * de varios bancos: así se modela un cliente con cuentas en 2 o 3 bancos (sección 8).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cliente {

    private String id;

    private String nombre;

    @Builder.Default
    private List<Cuenta> cuentas = new ArrayList<>();
}
