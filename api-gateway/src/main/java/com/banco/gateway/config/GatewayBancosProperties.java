package com.banco.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Bancos que el gateway conoce (prefijo de ruta + URL base). Se enlaza con
 * la sección {@code gateway.bancos} del application.yml; en Docker las URLs
 * llegan por variables de entorno ({@code BANCO_A_URL}, …).
 */
@ConfigurationProperties(prefix = "gateway")
public class GatewayBancosProperties {

    private List<Banco> bancos = new ArrayList<>();

    public List<Banco> getBancos() {
        return bancos;
    }

    public void setBancos(List<Banco> bancos) {
        this.bancos = bancos;
    }

    public static class Banco {

        /** Id lógico, p. ej. {@code BANCO_A}. */
        private String id;

        /** Prefijo de ruta en el gateway, p. ej. {@code a} (rutas {@code /a/**}). */
        private String prefijo;

        /** URL base del banco, p. ej. {@code http://banco-a:8081}. */
        private String url;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getPrefijo() {
            return prefijo;
        }

        public void setPrefijo(String prefijo) {
            this.prefijo = prefijo;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }
    }
}
