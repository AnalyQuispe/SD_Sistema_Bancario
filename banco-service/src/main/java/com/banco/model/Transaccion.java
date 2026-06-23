package com.banco.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Registro de una operación. Persistir el {@link EstadoTransaccion} es necesario para
 * la recuperación del 2PC y como evidencia para el informe (sección 12.5).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaccion {

    private String id;

    private TipoTransaccion tipo;

    /** Cuenta origen (null en un depósito puro). */
    private String origen;

    /** Cuenta destino (null en un retiro puro). */
    private String destino;

    private BigDecimal monto;

    private EstadoTransaccion estado;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant timestamp;
}
