package com.banco.exception;

/** Se lanza cuando una cuenta no tiene saldo suficiente para un retiro o transferencia. */
public class SaldoInsuficienteException extends RuntimeException {
    public SaldoInsuficienteException(String numeroCuenta) {
        super("Saldo insuficiente en la cuenta: " + numeroCuenta);
    }
}
