package com.banco.controller;

import com.banco.coordinator.EleccionCoordinador;
import com.banco.model.dto.EleccionMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints <b>internos</b> del algoritmo de elección Bully (Hito 2 · Integrante 3).
 *
 * <p>Solo los llaman los otros bancos a través de {@code BancoRemotoClient}; nunca se
 * exponen al frontend (convención {@code /internal/**}, protegidos por
 * {@link com.banco.config.InternalAuthFilter}).
 */
@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class EleccionController {

    private final EleccionCoordinador eleccion;

    /**
     * Otro nodo envía un mensaje ELECCION (pregunta si estoy vivo y tengo mayor prioridad).
     * Respondo OK y tomo el control de la elección.
     */
    @PostMapping("/eleccion")
    public Map<String, String> recibirEleccion(@RequestBody EleccionMessage msg) {
        return eleccion.recibirEleccion(msg);
    }

    /**
     * El nodo ganador anuncia que es el nuevo coordinador.
     */
    @PostMapping("/coordinador")
    public void recibirCoordinador(@RequestBody EleccionMessage msg) {
        eleccion.recibirCoordinador(msg);
    }

    /** Consulta quién es el coordinador actual según este nodo. */
    @GetMapping("/coordinador")
    public Map<String, String> consultarCoordinador() {
        String coordinador = eleccion.getCoordinadorActual();
        return Map.of("coordinador", coordinador != null ? coordinador : "DESCONOCIDO");
    }

    /**
     * Fuerza una elección manualmente. Útil para la demo: apagar un nodo,
     * llamar a este endpoint, y ver en los logs cómo se elige un nuevo coordinador.
     */
    @PostMapping("/eleccion/forzar")
    public Map<String, String> forzarEleccion() {
        String coordinador = eleccion.iniciarEleccion();
        return Map.of("coordinador", coordinador != null ? coordinador : "EN_PROCESO");
    }
}
