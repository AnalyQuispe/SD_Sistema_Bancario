package com.banco.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo mínimo de los mensajes COMMIT y ABORT del 2PC: basta el {@code txId}
 * porque el participante ya guardó la reserva asociada durante el PREPARE.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TxRequest {
    private String txId;
}
