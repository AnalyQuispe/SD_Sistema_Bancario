package com.banco.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Mensaje del algoritmo de elección Bully (Hito 2 · Integrante 3).
 *
 * <p>Se usa tanto para el mensaje {@code ELECCION} (un nodo pregunta a los de
 * mayor prioridad) como para el anuncio {@code COORDINADOR} (el ganador se
 * anuncia a todos).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EleccionMessage {

    /** Banco que emite el mensaje, p. ej. {@code BANCO_A}. */
    private String bancoId;

    /** Prioridad numérica del emisor (en Bully, gana el mayor). */
    private int prioridad;

    /** Tipo de mensaje: {@code "ELECCION"} o {@code "COORDINADOR"}. */
    private String tipo;
}
