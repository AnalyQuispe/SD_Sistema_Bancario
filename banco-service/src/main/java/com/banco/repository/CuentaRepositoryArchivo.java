package com.banco.repository;

import com.banco.config.BancoProperties;
import com.banco.event.BancoDataChangedEvent;
import com.banco.model.BancoData;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Repository;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * Persistencia en <b>archivo JSON local</b> (implementación por defecto de {@link CuentaRepository}).
 * Activa con {@code banco.persistencia=archivo} (o si la propiedad no está definida).
 *
 * <p>Cumple el requisito "la información de las cuentas se almacena en archivos":
 * <ul>
 *   <li>Serialización con Jackson ({@link ObjectMapper}).</li>
 *   <li>Bloqueo de archivo con {@link FileLock} (Java NIO) para escrituras seguras entre procesos.</li>
 *   <li>Escritura atómica vía archivo temporal + {@code ATOMIC_MOVE} para que un fallo a mitad de
 *       escritura nunca deje el JSON corrupto.</li>
 * </ul>
 *
 * <p>Mantiene una copia en memoria ({@code cache}) como fuente de verdad durante la ejecución.
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "banco.persistencia", havingValue = "archivo", matchIfMissing = true)
@RequiredArgsConstructor
public class CuentaRepositoryArchivo implements CuentaRepository {

    private final BancoProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** Serializa todo acceso a disco dentro de este proceso (evita locks solapados). */
    private final Object fileMutex = new Object();

    private Path dataPath;
    private volatile BancoData cache;

    @PostConstruct
    void init() throws IOException {
        dataPath = Paths.get(properties.getDataFile()).toAbsolutePath();
        if (Files.notExists(dataPath)) {
            inicializarDesdeSemilla();
        }
        cache = readFromDisk();
        log.info("Banco {} cargó {} cliente(s) desde archivo {}",
                properties.getId(), cache.getClientes().size(), dataPath);
    }

    /** Copia el JSON semilla del classpath al archivo de datos externo la primera vez. */
    private void inicializarDesdeSemilla() throws IOException {
        Path parent = dataPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (InputStream in = new ClassPathResource(properties.getSeedResource()).getInputStream()) {
            Files.copy(in, dataPath);
        }
        log.info("Archivo de datos inicializado desde semilla '{}' -> {}",
                properties.getSeedResource(), dataPath);
    }

    @Override
    public BancoData load() {
        return cache;
    }

    private BancoData readFromDisk() throws IOException {
        synchronized (fileMutex) {
            try (RandomAccessFile raf = new RandomAccessFile(dataPath.toFile(), "rw");
                 FileChannel channel = raf.getChannel();
                 FileLock ignored = channel.lock(0L, Long.MAX_VALUE, true)) { // lock compartido (lectura)
                return objectMapper.readValue(Files.readAllBytes(dataPath), BancoData.class);
            }
        }
    }

    @Override
    public void save(BancoData data) {
        synchronized (fileMutex) {
            try {
                data.setVersion(data.getVersion() + 1);
                byte[] bytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(data);
                Path tmp = dataPath.resolveSibling(dataPath.getFileName() + ".tmp");
                try (RandomAccessFile raf = new RandomAccessFile(tmp.toFile(), "rw");
                     FileChannel channel = raf.getChannel();
                     FileLock ignored = channel.lock()) { // lock exclusivo (escritura)
                    channel.truncate(0);
                    channel.write(ByteBuffer.wrap(bytes));
                    channel.force(true);
                }
                Files.move(tmp, dataPath,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                cache = data;
                log.debug("Estado del banco {} persistido en archivo {}", properties.getId(), dataPath);
                eventPublisher.publishEvent(new BancoDataChangedEvent(this, data));
            } catch (IOException e) {
                throw new IllegalStateException("Error al persistir el archivo del banco", e);
            }
        }
    }
}
