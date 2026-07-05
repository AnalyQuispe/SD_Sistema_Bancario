package com.banco.gateway.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Emisión y validación de tokens JWT firmados con HS256 (Aporte A · Seguridad distribuida).
 *
 * <p>El token incluye el {@code clienteId} (subject) y su rol ({@code CLIENTE}/{@code ADMIN}).
 * El secreto es compartido vía configuración (en Docker, variable {@code JWT_SECRET}); con
 * HS256 debe tener al menos 32 bytes.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROL = "rol";

    private final SecretKey clave;
    private final Duration duracion;

    public JwtService(@Value("${seguridad.jwt-secret}") String secreto,
                      @Value("${seguridad.expiracion-minutos:480}") long expiracionMinutos) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracion = Duration.ofMinutes(expiracionMinutos);
    }

    /** Emite un token para el cliente autenticado. */
    public String emitir(String clienteId, String rol) {
        Instant ahora = Instant.now();
        return Jwts.builder()
                .subject(clienteId)
                .claim(CLAIM_ROL, rol)
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(duracion)))
                .signWith(clave)
                .compact();
    }

    /**
     * Valida firma y expiración; devuelve los claims.
     *
     * @throws JwtException si el token es inválido, está manipulado o expiró
     */
    public Claims validar(String token) {
        return Jwts.parser()
                .verifyWith(clave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
