package com.banco.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filtro global de seguridad del gateway (Aporte A · Seguridad distribuida).
 *
 * <p>Se ejecuta antes de enrutar cualquier petición hacia los bancos:
 * <ol>
 *   <li><b>Perímetro:</b> ninguna ruta que contenga {@code /internal/} se enruta jamás
 *       (defensa en profundidad: además los bancos exigen su secreto compartido).</li>
 *   <li><b>Autenticación:</b> toda petición a {@code /x/api/**} necesita un JWT válido
 *       ({@code Authorization: Bearer …}); sin él → {@code 401}.</li>
 *   <li><b>Autorización (RBAC):</b> las rutas de un cliente concreto
 *       ({@code /x/api/clientes/{id}/**}) solo son accesibles por ese mismo cliente
 *       o por un {@code ADMIN}; en otro caso → {@code 403}.</li>
 * </ol>
 *
 * <p>Rutas públicas: el login ({@code /auth/**}), los healthchecks
 * ({@code /x/actuator/health}, usados por el indicador de "banco vivo" del frontend)
 * y los preflight CORS ({@code OPTIONS}).
 */
@Component
public class JwtAuthGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthGlobalFilter.class);

    /** Ruta de salud de un banco: pública para poder pintar el semáforo sin sesión. */
    private static final Pattern RUTA_SALUD = Pattern.compile("^/[a-c]/actuator/health/?$");

    /** Ruta con dueño explícito: /a/api/clientes/{id}/... */
    private static final Pattern RUTA_CLIENTE = Pattern.compile("^/[a-c]/api/clientes/([^/]+)(/.*)?$");

    private final JwtService jwtService;

    public JwtAuthGlobalFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ruta = exchange.getRequest().getPath().value();

        // Preflight CORS: siempre pasa (lo resuelve el CorsWebFilter).
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return chain.filter(exchange);
        }

        // El gateway NUNCA expone los endpoints internos banco-a-banco.
        if (ruta.contains("/internal/")) {
            log.warn("Bloqueado intento de acceso a endpoint interno vía gateway: {}", ruta);
            return rechazar(exchange, HttpStatus.FORBIDDEN,
                    "Los endpoints internos no se exponen a través del gateway");
        }

        // Rutas públicas: login y healthchecks.
        if (ruta.startsWith("/auth/") || RUTA_SALUD.matcher(ruta).matches()) {
            return chain.filter(exchange);
        }

        // Autenticación: JWT obligatorio.
        String autorizacion = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (autorizacion == null || !autorizacion.startsWith("Bearer ")) {
            return rechazar(exchange, HttpStatus.UNAUTHORIZED,
                    "Falta el token: inicia sesión en POST /auth/login");
        }

        Claims claims;
        try {
            claims = jwtService.validar(autorizacion.substring("Bearer ".length()));
        } catch (JwtException e) {
            log.warn("Token rechazado para {}: {}", ruta, e.getMessage());
            return rechazar(exchange, HttpStatus.UNAUTHORIZED, "Token inválido o expirado");
        }

        String clienteId = claims.getSubject();
        String rol = String.valueOf(claims.get(JwtService.CLAIM_ROL));

        // Autorización: un cliente solo puede ver/operar SUS cuentas.
        Matcher rutaCliente = RUTA_CLIENTE.matcher(ruta);
        if (rutaCliente.matches() && !"ADMIN".equals(rol) && !clienteId.equals(rutaCliente.group(1))) {
            log.warn("RBAC: {} intentó acceder a datos del cliente {}", clienteId, rutaCliente.group(1));
            return rechazar(exchange, HttpStatus.FORBIDDEN,
                    "No autorizado: solo puedes acceder a tus propias cuentas");
        }

        // Propaga la identidad al banco destino (evidencia en logs / auditoría).
        ServerWebExchange conIdentidad = exchange.mutate()
                .request(req -> req.headers(h -> {
                    h.set("X-Cliente-Id", clienteId);
                    h.set("X-Cliente-Rol", rol);
                }))
                .build();
        return chain.filter(conIdentidad);
    }

    private Mono<Void> rechazar(ServerWebExchange exchange, HttpStatus status, String mensaje) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String cuerpo = "{\"status\":" + status.value()
                + ",\"error\":\"" + status.getReasonPhrase()
                + "\",\"message\":\"" + mensaje + "\"}";
        DataBuffer buffer = exchange.getResponse().bufferFactory()
                .wrap(cuerpo.getBytes(StandardCharsets.UTF_8));
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // antes que los filtros de enrutamiento
    }
}
