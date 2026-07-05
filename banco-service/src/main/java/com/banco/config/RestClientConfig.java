package com.banco.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Cliente HTTP para hablar con los otros bancos (peers).
 *
 * <p>Se usan timeouts cortos a propósito: si un banco está caído, la llamada debe
 * fallar rápido en vez de dejar colgado a todo el sistema. Así la vista global de
 * cuentas puede responder con los bancos vivos aunque uno no conteste (Integrante 1).
 */
@Configuration
public class RestClientConfig {

    /** Tiempo máximo para abrir la conexión con un peer. */
    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);

    /** Tiempo máximo para esperar la respuesta de un peer. */
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(2);

    @Bean
    public RestClient restClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_CONEXION);
        factory.setReadTimeout(TIMEOUT_LECTURA);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
