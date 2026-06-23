package com.banco.repository;

import com.banco.config.BancoProperties;
import com.banco.model.BancoData;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * Persistencia de las cuentas de este banco en su archivo JSON local.
 *
 * <p>Implementa el requisito de la consigna "la información de las cuentas se almacena
 * en archivos":
 * <ul>
 *   <li>Serialización con Jackson ({@link ObjectMapper}).</li>
 *   <li>Bloqueo de archivo con {@link FileLock} (Java NIO) para escrituras seguras
 *       entre procesos.</li>
 *   <li>Escritura atómica vía archivo temporal + {@code ATOMIC_MOVE} para que un fallo
 *       a mitad de escritura nunca deje el JSON corrupto.</li>
 * </ul>
 *
 * <p>Mantiene una copia en memoria ({@code cache}) como fuente de verdad durante la
 * ejecución; cada {@link #save(BancoData)} la actualiza y la vuelca a disco.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class CuentaRepository {

    private final BancoProperties properties;
    private final ObjectMapper objectMapper;

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
        log.info("Banco {} cargó {} cliente(s) desde {}",
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

    /** Devuelve los datos del banco (copia en memoria, ya cargada desde disco). */
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

    /**
     * Persiste {@code data} de forma atómica y segura ante concurrencia entre procesos.
     * Actualiza también la copia en memoria.
     */
    public void save(BancoData data) {
        synchronized (fileMutex) {
            try {
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
                log.debug("Estado del banco {} persistido en {}", properties.getId(), dataPath);
            } catch (IOException e) {
                throw new IllegalStateException("Error al persistir el archivo del banco", e);
            }
        }
    }
}
