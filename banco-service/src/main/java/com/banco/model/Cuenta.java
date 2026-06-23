package com.banco.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cuenta bancaria. El saldo usa {@link BigDecimal} para evitar errores de redondeo
 * propios de {@code double} en operaciones monetarias.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cuenta {

    /** Número único de cuenta, p. ej. {@code A-1001}. */
    private String numero;

    private BigDecimal saldo;

    /** Moneda ISO, p. ej. {@code USD}. */
    private String moneda;

    /** Banco propietario de la cuenta, p. ej. {@code BANCO_A}. */
    private String bancoId;
}
