package com.banco.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuración propia de cada instancia de banco.
 *
 * <p>Se enlaza con el prefijo {@code banco.*} de los archivos
 * {@code application-bancoX.yml}. Permite que el mismo código se comporte como
 * Banco A, B o C según el perfil activo.
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

    /**
     * Los otros dos bancos con los que este nodo se comunica (Hito 2, Integrante 1).
     * En el perfil de Banco A serán B y C; en B serán A y C; etc.
     */
    private List<Peer> peers = new ArrayList<>();

    private ReplicacionProperties replicacion = new ReplicacionProperties();

    /** Un banco remoto al que este nodo puede llamar por REST. */
    @Data
    public static class Peer {

        /** Identificador del banco remoto, p. ej. {@code BANCO_B}. */
        private String id;

        /** URL base del banco remoto, p. ej. {@code http://localhost:8082}. */
        private String url;
    }

    @Data
    public static class ReplicacionProperties {
        private String destinoPeerId;
        private String replicasDir = "./data/replicas";
    }
}
