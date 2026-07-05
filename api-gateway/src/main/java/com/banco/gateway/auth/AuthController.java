package com.banco.gateway.auth;

import com.banco.gateway.config.GatewayBancosProperties;
import com.banco.gateway.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Login del sistema (Aporte A · autenticación distribuida con JWT).
 *
 * <p>El cliente se identifica con su {@code clienteId}. El gateway verifica que el
 * cliente <b>exista en la red de bancos</b> preguntando a los bancos en orden: el primer
 * banco vivo responde por toda la red (la consulta de cuentas ya es global gracias al
 * Integrante 1). Si el cliente existe se emite un JWT firmado (HS256) con su id y rol.
 *
 * <p>Respuestas: {@code 200} con token · {@code 401} cliente inexistente ·
 * {@code 503} ningún banco disponible.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    /** Resultado de preguntar a un banco por el cliente. */
    private enum Resultado { EXISTE, NO_EXISTE, BANCO_CAIDO }

    private final GatewayBancosProperties propiedades;
    private final JwtService jwtService;
    private final WebClient webClient;

    public AuthController(GatewayBancosProperties propiedades,
                          JwtService jwtService,
                          WebClient.Builder webClientBuilder) {
        this.propiedades = propiedades;
        this.jwtService = jwtService;
        this.webClient = webClientBuilder.build();
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, String>>> login(@RequestBody LoginRequest peticion) {
        String clienteId = peticion.clienteId() == null ? "" : peticion.clienteId().trim();
        if (clienteId.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(Map.of("message", "Falta el clienteId")));
        }

        // Rol ADMIN de demostración (RBAC): puede consultar cualquier cliente.
        if ("ADMIN".equalsIgnoreCase(clienteId)) {
            return Mono.just(respuestaOk("ADMIN", "ADMIN"));
        }

        // Se pregunta a los 3 bancos EN PARALELO (flatMap): la vista de clientes de
        // cualquier banco es global, así que basta con que UN banco vivo confirme.
        // Consultarlos en serie multiplicaría por 3 la espera cuando un banco está caído.
        return Flux.fromIterable(propiedades.getBancos())
                .flatMap(banco -> existeCliente(banco, clienteId))
                .collectList()
                .map(resultados -> {
                    if (resultados.contains(Resultado.EXISTE)) {
                        log.info("Login correcto del cliente {}", clienteId);
                        return respuestaOk(clienteId, "CLIENTE");
                    }
                    if (resultados.contains(Resultado.NO_EXISTE)) {
                        log.warn("Login rechazado: el cliente {} no existe en ningún banco", clienteId);
                        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                                .body(Map.of("message", "Cliente no registrado en ningún banco"));
                    }
                    log.warn("Login imposible para {}: ningún banco respondió", clienteId);
                    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                            .body(Map.of("message", "Ningún banco disponible para validar el login"));
                });
    }

    private ResponseEntity<Map<String, String>> respuestaOk(String clienteId, String rol) {
        return ResponseEntity.ok(Map.of(
                "token", jwtService.emitir(clienteId, rol),
                "clienteId", clienteId,
                "rol", rol));
    }

    /**
     * Pregunta a UN banco si el cliente tiene cuentas (en toda la red).
     *
     * <p>El endpoint consultado es la vista global del banco, que a su vez consulta a sus
     * peers; si uno está caído, el banco espera su propio timeout (~2 s) antes de responder
     * con los bancos vivos. Por eso el timeout de aquí es holgado (6 s): debe superar la
     * espera interna del banco para no marcar como "caído" a un banco que en realidad
     * responde, solo que un poco más lento por el peer caído.
     */
    private Mono<Resultado> existeCliente(GatewayBancosProperties.Banco banco, String clienteId) {
        return webClient.get()
                .uri(banco.getUrl() + "/api/clientes/{id}/cuentas", clienteId)
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(6))
                .map(respuesta -> Resultado.EXISTE)
                .onErrorResume(WebClientResponseException.NotFound.class,
                        e -> Mono.just(Resultado.NO_EXISTE))
                .onErrorResume(e -> {
                    log.warn("Banco {} no disponible durante el login: {}", banco.getId(), e.getMessage());
                    return Mono.just(Resultado.BANCO_CAIDO);
                });
    }
}
