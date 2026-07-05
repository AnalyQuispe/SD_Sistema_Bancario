package com.banco.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Habilita CORS para la API del banco. Necesario para que la interfaz de prueba
 * (servida en otro origen, p. ej. {@code http://localhost:5500} o un archivo local)
 * pueda llamar a los endpoints REST desde el navegador.
 *
 * <p>Configuración pensada para desarrollo/demostración del Hito 1: permite cualquier
 * origen sobre las rutas {@code /api/**}. En un entorno real se restringiría a orígenes
 * concretos.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS");
    }
}
