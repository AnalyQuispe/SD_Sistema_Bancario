package com.banco.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * API Gateway del sistema bancario distribuido (Hito 2 · Integrante 5).
 *
 * <p>Es el <b>punto único de entrada</b>: el frontend solo habla con este servicio
 * (puerto 8080) y el gateway enruta cada petición al banco elegido
 * ({@code /a/**} → Banco A, {@code /b/**} → Banco B, {@code /c/**} → Banco C).
 *
 * <p>Responsabilidades:
 * <ul>
 *   <li><b>Enrutamiento</b> declarativo (application.yml) hacia los 3 bancos.</li>
 *   <li><b>Autenticación</b>: emite JWT en {@code POST /auth/login} y lo valida en cada
 *       petición ({@link com.banco.gateway.security.JwtAuthGlobalFilter}).</li>
 *   <li><b>Autorización (RBAC)</b>: un cliente solo accede a sus propias cuentas.</li>
 *   <li><b>Perímetro</b>: los endpoints internos {@code /internal/**} de los bancos
 *       jamás se enrutan hacia fuera.</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
