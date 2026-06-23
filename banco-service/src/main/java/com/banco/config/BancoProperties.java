package com.banco.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración propia de cada instancia de banco.
 *
 * <p>Se enlaza con el prefijo {@code banco.*} de los archivos
 * {@code application-bancoX.yml}. Permite que el mismo código se comporte como
 * Banco A, B o C según el perfil activo.
 *
 * <p>En el Hito 2 se añadirán aquí la lista de <em>peers</em> y la carpeta de réplicas.
 */
@Data
@ConfigurationProperties(prefix = "banco")
public class BancoProperties {

    /** Identificador lógico del banco, p. ej. {@code BANCO_A}. */
    private String id;

    /** Nombre legible para la UI, p. ej. {@code Banco A}. */
    private String nombre;

    /** Ruta (externa al jar) del archivo JSON donde este banco persiste sus cuentas. */
    private String dataFile;

    /** Recurso del classpath con los datos semilla, usado si {@code dataFile} no existe aún. */
    private String seedResource;
}
