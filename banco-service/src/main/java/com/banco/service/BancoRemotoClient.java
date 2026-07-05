package com.banco.service;

import com.banco.config.BancoProperties;
import com.banco.config.BancoProperties.Peer;
import com.banco.model.Cuenta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Puerta de salida de este banco hacia los otros bancos (los "peers").
 *
 * <p>Es el <b>cimiento del Hito 2</b>: aquí se concentra toda la comunicación REST entre
 * bancos. Los Integrantes 2 (2PC), 3 (coordinación) y 4 (replicación) reutilizan estos
 * métodos —en especial {@link #postInternal} y {@link #getInternal}— para hablar con los peers.
 *
 * <p>Regla de tolerancia a fallos: si un peer no responde, <b>no</b> se propaga el error;
 * se registra un aviso y se devuelve un resultado vacío. Así una consulta global sigue
 * funcionando aunque un banco esté caído.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BancoRemotoClient {

    private final BancoProperties properties;
    private final RestClient restClient;

    // ------------------------------------------------------------ vista global

    /**
     * Junta las cuentas de un cliente que viven en los otros bancos.
     *
     * <p>Pregunta a cada peer por su parte <b>local</b> (endpoint {@code -locales}, que NO
     * vuelve a preguntar a nadie) para evitar recursión infinita entre bancos. Las llamadas
     * se hacen en paralelo; un peer caído simplemente no aporta cuentas.
     */
    public List<Cuenta> cuentasDeClienteEnPeers(String clienteId) {
        return properties.getPeers().parallelStream()
                .flatMap(peer -> cuentasLocalesEnPeer(peer, clienteId).stream())
                .toList();
    }

    private List<Cuenta> cuentasLocalesEnPeer(Peer peer, String clienteId) {
        try {
            List<Cuenta> cuentas = restClient.get()
                    .uri(peer.getUrl() + "/internal/clientes/{id}/cuentas-locales", clienteId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Cuenta>>() {});
            log.info("Peer {} respondió {} cuenta(s) del cliente {}",
                    peer.getId(), cuentas == null ? 0 : cuentas.size(), clienteId);
            return cuentas == null ? List.of() : cuentas;
        } catch (RestClientException e) {
            log.warn("Peer {} ({}) no respondió al consultar cuentas de {}: {}",
                    peer.getId(), peer.getUrl(), clienteId, e.getMessage());
            return List.of();
        }
    }

    // ------------------------------------------------------ búsqueda de cuentas

    /**
     * Busca una cuenta que pertenece a otro banco.
     *
     * <p>Deduce el banco dueño por el prefijo del número (ver {@link #bancoDueno}) y consulta
     * solo a ese peer. Devuelve vacío si la cuenta es local, si el peer no está configurado
     * o si no responde. Lo usará el Integrante 2 para localizar el destino de un 2PC.
     */
    public Optional<Cuenta> buscarCuentaRemota(String numeroCuenta) {
        String dueno = bancoDueno(numeroCuenta);
        if (dueno.equals(properties.getId())) {
            return Optional.empty(); // la cuenta es de este banco, no es remota
        }
        return peerPorId(dueno).flatMap(peer -> consultarCuentaEnPeer(peer, numeroCuenta));
    }

    private Optional<Cuenta> consultarCuentaEnPeer(Peer peer, String numeroCuenta) {
        try {
            Cuenta cuenta = restClient.get()
                    .uri(peer.getUrl() + "/internal/cuentas/{numero}", numeroCuenta)
                    .retrieve()
                    .body(Cuenta.class);
            return Optional.ofNullable(cuenta);
        } catch (RestClientException e) {
            log.warn("Peer {} ({}) no devolvió la cuenta {}: {}",
                    peer.getId(), peer.getUrl(), numeroCuenta, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Banco al que pertenece una cuenta, según la convención de nombres del proyecto:
     * el prefijo {@code A-}, {@code B-} o {@code C-} indica el banco (p. ej. {@code C-3001 → BANCO_C}).
     */
    public String bancoDueno(String numeroCuenta) {
        char inicial = Character.toUpperCase(numeroCuenta.charAt(0));
        return "BANCO_" + inicial;
    }

    // ----------------------------------------- llamadas genéricas reutilizables

    /**
     * POST genérico a un endpoint interno de un peer. Base para el 2PC (Integrante 2),
     * la coordinación (Integrante 3) y la replicación (Integrante 4).
     *
     * @param peerUrl        URL base del peer, p. ej. {@code http://localhost:8082}
     * @param path           ruta interna, p. ej. {@code /internal/2pc/prepare}
     * @param cuerpo         objeto a enviar como JSON (puede ser {@code null})
     * @param tipoRespuesta  clase esperada en la respuesta
     */
    public <T> T postInternal(String peerUrl, String path, Object cuerpo, Class<T> tipoRespuesta) {
        RestClient.RequestBodySpec peticion = restClient.post().uri(peerUrl + path);
        if (cuerpo != null) {
            peticion.body(cuerpo);
        }
        return peticion.retrieve().body(tipoRespuesta);
    }

    /** GET genérico a un endpoint interno de un peer (contraparte de {@link #postInternal}). */
    public <T> T getInternal(String peerUrl, String path, Class<T> tipoRespuesta) {
        return restClient.get().uri(peerUrl + path).retrieve().body(tipoRespuesta);
    }

    /**
     * URL base del peer con ese id de banco, si está configurado. La usan el coordinador 2PC
     * (Integrante 2), la coordinación (Integrante 3) y la replicación (Integrante 4) para
     * dirigir un mensaje a un banco concreto por su id lógico ({@code BANCO_A/B/C}).
     */
    public Optional<String> urlDePeer(String bancoId) {
        return peerPorId(bancoId).map(Peer::getUrl);
    }

    // ----------------------------------------------------------------- helpers

    private Optional<Peer> peerPorId(String bancoId) {
        return properties.getPeers().stream()
                .filter(peer -> peer.getId().equals(bancoId))
                .findFirst();
    }
}
