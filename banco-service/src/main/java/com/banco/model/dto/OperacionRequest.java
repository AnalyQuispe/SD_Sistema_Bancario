package com.banco.model.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/** Cuerpo de un depósito o retiro sobre una cuenta. */
@Data
public class OperacionRequest {

    @NotBlank
    private String cuenta;

    @NotNull
    @DecimalMin(value = "0.01", message = "El monto debe ser positivo")
    private BigDecimal monto;
}
