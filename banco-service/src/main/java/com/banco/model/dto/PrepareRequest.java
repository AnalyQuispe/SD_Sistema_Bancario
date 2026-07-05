package com.banco.model.dto;

import com.banco.model.RolParticipante;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Cuerpo del mensaje PREPARE que el coordinador envía a un participante del 2PC.
 *
 * @see com.banco.coordinator.TwoPhaseCommitParticipant
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PrepareRequest {

    /** Identificador único de la transacción distribuida, p. ej. {@code TX-1a2b3c4d}. */
    private String txId;

    /** Cuenta local del participante afectada por esta fase. */
    private String cuenta;

    /** Qué debe hacer el participante: reservar un débito o preparar un crédito. */
    private RolParticipante rol;

    /** Importe de la operación (siempre positivo). */
    private BigDecimal monto;
}
