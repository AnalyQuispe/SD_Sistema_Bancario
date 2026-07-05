package com.banco.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS del gateway. El frontend (React, servido en otro origen) habla únicamente
 * con el gateway, así que este es el único sitio donde hace falta permitirlo.
 *
 * <p>Cubre tanto las rutas enrutadas ({@code /a/**}, …) como los endpoints propios
 * del gateway ({@code /auth/**}). Los duplicados de cabecera con los bancos se
 * eliminan con el filtro {@code DedupeResponseHeader} (application.yml).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
