package com.banco.model.dto;

import com.banco.model.Voto;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Respuesta de un participante a un mensaje PREPARE: su {@link Voto} y el motivo
 * (útil para el log y el informe cuando el voto es {@code NO}).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VotoResponse {

    private String txId;
    private Voto voto;
    private String motivo;

    public static VotoResponse yes(String txId) {
        return new VotoResponse(txId, Voto.YES, "OK");
    }

    public static VotoResponse no(String txId, String motivo) {
        return new VotoResponse(txId, Voto.NO, motivo);
    }
}
