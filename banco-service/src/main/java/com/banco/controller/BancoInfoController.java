package com.banco.controller;

import com.banco.config.BancoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Identidad del banco; la usa el frontend para mostrar a qué nodo está conectado. */
@RestController
@RequestMapping("/api/banco")
@RequiredArgsConstructor
public class BancoInfoController {

    private final BancoProperties properties;

    @GetMapping
    public Map<String, String> info() {
        return Map.of(
                "id", properties.getId(),
                "nombre", properties.getNombre());
    }
}
