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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Persistencia en <b>base de datos SQLite embebida</b> (implementación alternativa de
 * {@link CuentaRepository}). Se activa con {@code banco.persistencia=db} (o la variable de
 * entorno {@code BANCO_PERSISTENCIA=db}).
 *
 * <p>Demuestra que el sistema es <b>configurable en aspectos de la base de datos</b>: el mismo
 * código de negocio funciona sobre archivo JSON o sobre una BD relacional real sin cambios,
 * solo alterando una propiedad. Guarda todo el estado del banco como un documento JSON en una
 * única fila de la tabla {@code banco_data} (reutiliza la misma serialización Jackson que la
 * versión de archivo). SQLite persiste, a su vez, en un archivo {@code .db} local.
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "banco.persistencia", havingValue = "db")
@RequiredArgsConstructor
public class CuentaRepositorySqlite implements CuentaRepository {

    private final BancoProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** Serializa las escrituras dentro de este proceso. */
    private final Object mutex = new Object();

    private String jdbcUrl;
    private volatile BancoData cache;

    @PostConstruct
    void init() throws Exception {
        Path dbPath = Paths.get(rutaDb()).toAbsolutePath();
        if (dbPath.getParent() != null) {
            Files.createDirectories(dbPath.getParent());
        }
        jdbcUrl = "jdbc:sqlite:" + dbPath;

        try (Connection cn = abrir(); Statement st = cn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS banco_data ("
                    + "id INTEGER PRIMARY KEY CHECK (id = 1), json TEXT NOT NULL)");
        }

        String json = leerJson();
        if (json == null) {
            json = leerSemilla();
            guardarJson(json);
            log.info("Base de datos SQLite inicializada desde semilla '{}' -> {}",
                    properties.getSeedResource(), dbPath);
        }
        cache = objectMapper.readValue(json, BancoData.class);
        log.info("Banco {} cargó {} cliente(s) desde SQLite {}",
                properties.getId(), cache.getClientes().size(), dbPath);
    }

    @Override
    public BancoData load() {
        return cache;
    }

    @Override
    public void save(BancoData data) {
        synchronized (mutex) {
            try {
                data.setVersion(data.getVersion() + 1);
                guardarJson(objectMapper.writeValueAsString(data));
                cache = data;
                log.debug("Estado del banco {} persistido en SQLite", properties.getId());
                eventPublisher.publishEvent(new BancoDataChangedEvent(this, data));
            } catch (Exception e) {
                throw new IllegalStateException("Error al persistir en la base de datos SQLite", e);
            }
        }
    }

    // --------------------------------------------------------------- helpers JDBC

    private Connection abrir() throws SQLException {
        return DriverManager.getConnection(jdbcUrl);
    }

    private String leerJson() throws SQLException {
        try (Connection cn = abrir();
             PreparedStatement ps = cn.prepareStatement("SELECT json FROM banco_data WHERE id = 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private void guardarJson(String json) throws SQLException {
        try (Connection cn = abrir();
             PreparedStatement ps = cn.prepareStatement(
                     "INSERT INTO banco_data (id, json) VALUES (1, ?) "
                             + "ON CONFLICT(id) DO UPDATE SET json = excluded.json")) {
            ps.setString(1, json);
            ps.executeUpdate();
        }
    }

    private String leerSemilla() throws IOException {
        try (InputStream in = new ClassPathResource(properties.getSeedResource()).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Ruta del archivo SQLite: deriva de {@code data-file} cambiando la extensión .json por .db. */
    private String rutaDb() {
        String f = properties.getDataFile();
        return f.endsWith(".json") ? f.substring(0, f.length() - ".json".length()) + ".db" : f + ".db";
    }
}
