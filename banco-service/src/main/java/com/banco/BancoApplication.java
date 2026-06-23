package com.banco;

import com.banco.config.BancoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Punto de entrada del servicio de banco.
 *
 * <p>Un mismo artefacto se instancia 3 veces (Banco A, B y C) usando perfiles de Spring
 * distintos ({@code bancoA}, {@code bancoB}, {@code bancoC}). Cada instancia tiene su propio
 * id, puerto, archivo de datos y lista de <em>peers</em>, definidos en
 * {@code application-bancoX.yml}.
 */
@SpringBootApplication
@EnableConfigurationProperties(BancoProperties.class)
public class BancoApplication {

    public static void main(String[] args) {
        SpringApplication.run(BancoApplication.class, args);
    }
}
