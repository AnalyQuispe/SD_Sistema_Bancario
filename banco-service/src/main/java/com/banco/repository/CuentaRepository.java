package com.banco.repository;

import com.banco.model.BancoData;

/**
 * Abstracción de la persistencia de las cuentas de un banco (patrón Repository).
 *
 * <p>El almacenamiento es <b>configurable en aspectos de la base de datos</b>: se elige con la
 * propiedad {@code banco.persistencia} (o la variable de entorno {@code BANCO_PERSISTENCIA}):
 * <ul>
 *   <li>{@code archivo} (por defecto) → {@link CuentaRepositoryArchivo}: archivo JSON local
 *       (cumple "la información se almacena en archivos").</li>
 *   <li>{@code db} → {@link CuentaRepositorySqlite}: base de datos <b>SQLite</b> embebida.</li>
 * </ul>
 *
 * <p>Como el resto del sistema (servicios, 2PC, replicación) depende solo de esta interfaz,
 * cambiar de motor de almacenamiento <b>no afecta a la lógica de negocio</b>: se cambia en un
 * único punto.
 */
public interface CuentaRepository {

    /** Estado actual del banco (copia en memoria, ya cargada desde el almacenamiento). */
    BancoData load();

    /**
     * Persiste {@code data}, incrementa su versión y publica {@code BancoDataChangedEvent}
     * (que dispara la replicación del Integrante 4).
     */
    void save(BancoData data);
}
