package com.banco.exception;

/** Se lanza cuando una cuenta solicitada no existe en este banco. */
public class CuentaNoEncontradaException extends RuntimeException {
    public CuentaNoEncontradaException(String numeroCuenta) {
        super("Cuenta no encontrada: " + numeroCuenta);
    }
}
