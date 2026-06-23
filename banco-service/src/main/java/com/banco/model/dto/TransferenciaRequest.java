package com.banco.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Cuerpo de una transferencia. Si origen y destino pertenecen al mismo banco la
 * transferencia es local (Hito 1); si no, dispara el 2PC distribuido (Hito 2).
 */
@Data
public class TransferenciaRequest {

    @NotBlank
    private String cuentaOrigen;

    @NotBlank
    private String cuentaDestino;

    @NotNull
    @DecimalMin(value = "0.01", message = "El monto debe ser positivo")
    private BigDecimal monto;
}
