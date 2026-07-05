package com.banco.gateway.auth;

/** Cuerpo del login: el cliente se identifica con su id (p. ej. {@code C200}). */
public record LoginRequest(String clienteId) {
}
