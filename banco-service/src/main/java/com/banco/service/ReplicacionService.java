package com.banco.service;

import com.banco.config.BancoProperties;
import com.banco.event.BancoDataChangedEvent;
import com.banco.model.BancoData;
import com.banco.repository.CuentaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Servicio de replicación distribuida y tolerancia a fallos (Hito 2 · Integrante 4).
 *
 * <p>Maneja la replicación en anillo (A -> B -> C -> A), reintentos con backoff exponencial,
 * cola en memoria para reintentar cuando el peer destino vuelve a estar en línea, consistencia
 * por control de versiones en las réplicas, y restauración de base de datos desde réplicas remotas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplicacionService {

    private final BancoProperties properties;
    private final BancoRemotoClient remotoClient;
    private final CuentaRepository repository;
    private final ObjectMapper objectMapper;

    /** Cola en memoria de réplicas que fallaron y deben reintentarse. */
    private final Queue<BancoData> pendingQueue = new ConcurrentLinkedQueue<>();

    /** Pool de un solo hilo para procesar la replicación de forma asíncrona sin bloquear al usuario. */
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
    }

    /** Escucha el evento de modificación de base de datos local y dispara la replicación asíncrona. */
    @EventListener
    public void handleBancoDataChanged(BancoDataChangedEvent event) {
        replicarAsync(event.getData());
    }

    /** Envía la replicación en segundo plano. */
    public void replicarAsync(BancoData data) {
        executor.submit(() -> replicarSync(data));
    }

    /** Envía sincrónicamente con reintentos. Si falla, encola el envío en la cola de pendientes. */
    public void replicarSync(BancoData data) {
        String destinoId = properties.getReplicacion().getDestinoPeerId();
        if (destinoId == null || destinoId.isBlank()) {
            log.warn("Replicación no configurada (destinoPeerId vacío) para {}", properties.getId());
            return;
        }

        Optional<String> urlOpt = remotoClient.urlDePeer(destinoId);
        if (urlOpt.isEmpty()) {
            log.warn("No se encontró la URL del peer destino de réplica: {}", destinoId);
            return;
        }
        String targetUrl = urlOpt.get();

        int maxRetries = 3;
        int retryDelayMs = 1000;
        boolean success = false;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                remotoClient.postInternal(targetUrl, "/internal/replicar", data, Void.class);
                log.info("Replicación exitosa de {} versión {} hacia {} (Intento {})",
                        properties.getId(), data.getVersion(), destinoId, attempt);
                success = true;
                break;
            } catch (Exception e) {
                log.warn("Fallo en intento {} de replicación de {} hacia {}: {}",
                        attempt, properties.getId(), destinoId, e.getMessage());
                if (attempt < maxRetries) {
                    try {
                        Thread.sleep(retryDelayMs * attempt); // Backoff exponencial simple
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        if (!success) {
            log.error("Replicación de {} versión {} hacia {} falló tras {} intentos. Encolando.",
                    properties.getId(), data.getVersion(), destinoId, maxRetries);
            pendingQueue.add(data);
        }
    }

    /** Procesa periódicamente las réplicas que quedaron pendientes por caídas de peers. */
    @Scheduled(fixedDelay = 10000)
    public void procesarPendientes() {
        if (pendingQueue.isEmpty()) {
            return;
        }
        log.info("Detectadas réplicas pendientes en banco {}. Intentando enviar versión más reciente...", properties.getId());

        // Obtenemos la última versión disponible para no enviar estados obsoletos.
        BancoData latestData = repository.load();
        String destinoId = properties.getReplicacion().getDestinoPeerId();
        Optional<String> urlOpt = remotoClient.urlDePeer(destinoId);
        if (urlOpt.isEmpty()) {
            return;
        }
        String targetUrl = urlOpt.get();

        try {
            remotoClient.postInternal(targetUrl, "/internal/replicar", latestData, Void.class);
            log.info("Replicación de pendiente exitosa. Versión {} enviada a {}.", latestData.getVersion(), destinoId);
            pendingQueue.clear(); // Limpiamos la cola ya que se envió la última versión
        } catch (Exception e) {
            log.warn("El peer de destino {} sigue caído o sin responder: {}", destinoId, e.getMessage());
        }
    }

    /** Aplica una réplica entrante guardándola en un archivo separado de la base de datos principal. */
    public void aplicarReplica(BancoData peerData) {
        String replicasDir = properties.getReplicacion().getReplicasDir();
        Path replicaPath = Paths.get(replicasDir, "cuentas-" + peerData.getBancoId() + ".json").toAbsolutePath();

        try {
            Files.createDirectories(replicaPath.getParent());

            long currentVersion = -1;
            if (Files.exists(replicaPath)) {
                try {
                    BancoData currentReplica = objectMapper.readValue(Files.readAllBytes(replicaPath), BancoData.class);
                    currentVersion = currentReplica.getVersion();
                } catch (Exception e) {
                    log.warn("No se pudo leer la versión de la réplica existente de {}: {}",
                            peerData.getBancoId(), e.getMessage());
                }
            }

            if (peerData.getVersion() > currentVersion) {
                byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(peerData);
                Files.write(replicaPath, bytes);
                log.info("Réplica de {} aplicada exitosamente. Versión: {} (anterior: {})",
                        peerData.getBancoId(), peerData.getVersion(), currentVersion);
            } else {
                log.info("Réplica de {} rechazada: versión entrante ({}) <= guardada ({})",
                        peerData.getBancoId(), peerData.getVersion(), currentVersion);
            }
        } catch (IOException e) {
            log.error("Error al persistir la réplica de {} en disco: {}", peerData.getBancoId(), e.getMessage());
            throw new IllegalStateException("Error al guardar réplica", e);
        }
    }

    /** Lee una réplica específica de disco. */
    public Optional<BancoData> obtenerReplica(String bancoId) {
        String replicasDir = properties.getReplicacion().getReplicasDir();
        Path replicaPath = Paths.get(replicasDir, "cuentas-" + bancoId + ".json").toAbsolutePath();
        if (Files.notExists(replicaPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(Files.readAllBytes(replicaPath), BancoData.class));
        } catch (Exception e) {
            log.error("Error al leer réplica de {} en disco: {}", bancoId, e.getMessage());
            return Optional.empty();
        }
    }

    /** Lista las versiones de todas las réplicas almacenadas localmente. */
    public Map<String, Long> obtenerEstadoReplicas() {
        Map<String, Long> estado = new HashMap<>();
        String replicasDir = properties.getReplicacion().getReplicasDir();
        Path dirPath = Paths.get(replicasDir).toAbsolutePath();
        if (Files.exists(dirPath) && Files.isDirectory(dirPath)) {
            try (var stream = Files.list(dirPath)) {
                stream.filter(path -> path.getFileName().toString().startsWith("cuentas-") && path.getFileName().toString().endsWith(".json"))
                        .forEach(path -> {
                            try {
                                BancoData data = objectMapper.readValue(Files.readAllBytes(path), BancoData.class);
                                estado.put(data.getBancoId(), data.getVersion());
                            } catch (Exception e) {
                                log.warn("Error al leer versión de replica {} en estado: {}", path, e.getMessage());
                            }
                        });
            } catch (IOException e) {
                log.error("Error al listar directorio de réplicas: {}", e.getMessage());
            }
        }
        return estado;
    }

    /** Consulta a todos los peers por la réplica del banco actual, seleccionando la de versión más alta. */
    public String restaurarDesdeReplicas() {
        log.info("Iniciando restauración de datos desde réplicas de peers...");
        List<BancoData> candidates = new ArrayList<>();

        for (var peer : properties.getPeers()) {
            try {
                BancoData restored = remotoClient.getInternal(peer.getUrl(), "/internal/replica/" + properties.getId(), BancoData.class);
                if (restored != null) {
                    candidates.add(restored);
                    log.info("Réplica de nuestro banco encontrada en peer {} con versión {}", peer.getId(), restored.getVersion());
                }
            } catch (Exception e) {
                log.warn("No se pudo obtener réplica desde peer {} ({}): {}",
                        peer.getId(), peer.getUrl(), e.getMessage());
            }
        }

        BancoData bestCandidate = candidates.stream()
                .max(Comparator.comparingLong(BancoData::getVersion))
                .orElse(null);

        if (bestCandidate != null) {
            repository.save(bestCandidate);
            log.info("Banco {} restaurado exitosamente a la versión {} desde réplica de peers.",
                    properties.getId(), bestCandidate.getVersion());
            return String.format("Banco restaurado a la versión %d", bestCandidate.getVersion());
        } else {
            log.error("No se encontraron réplicas de {} en ningún peer vivo.", properties.getId());
            throw new IllegalStateException("No se encontraron réplicas para restaurar en los peers.");
        }
    }
}
