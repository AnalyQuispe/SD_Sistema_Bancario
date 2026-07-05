package com.banco.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Protección de los endpoints internos banco-a-banco (Aporte A · Seguridad distribuida).
 *
 * <p>Todo lo que cuelga de {@code /internal/**} (2PC, cuentas locales, replicación…) solo
 * debe poder llamarlo <b>otro banco</b> de la red, nunca un tercero. Cada banco exige la
 * cabecera {@code X-Internal-Token} con el secreto compartido del grupo de bancos; el
 * {@code RestClientConfig} añade esa cabecera a todas las llamadas salientes entre peers.
 *
 * <p>Sin el secreto (o con uno incorrecto) la respuesta es {@code 403 Forbidden}. El API
 * Gateway además ni siquiera enruta {@code /internal/**}: esta es la segunda línea de defensa
 * para llamadas directas a los puertos de los bancos.
 */
@Slf4j
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Internal-Token";

    /** Secreto compartido entre los bancos; en Docker se inyecta con INTERNAL_TOKEN. */
    @Value("${banco.internal-token}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/internal/")) {
            String recibido = request.getHeader(HEADER);
            if (!internalToken.equals(recibido)) {
                log.warn("Rechazada llamada a {} sin secreto interno válido (origen {})",
                        request.getRequestURI(), request.getRemoteAddr());
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.getWriter().write(
                        "{\"status\":403,\"error\":\"Forbidden\"," +
                        "\"message\":\"Endpoint interno: requiere el secreto compartido entre bancos\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
