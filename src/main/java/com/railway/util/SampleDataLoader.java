package com.railway.util;

import com.railway.db.DatabaseManager;
import com.railway.json.JsonDataManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

/**
 * Seeds a freshly created (empty) database with the bundled sample railway
 * network, copying the packaged resource to a temp file first since
 * {@link JsonDataManager} reads from a {@link Path} on disk.
 */
public final class SampleDataLoader {

    private SampleDataLoader() {
    }

    public static void seedIfEmpty(DatabaseManager db) throws SQLException, IOException {
        if (!db.isEmpty()) {
            return;
        }
        try (InputStream in = SampleDataLoader.class.getResourceAsStream("/data/sample_network.json")) {
            if (in == null) {
                throw new IOException("Bundled sample_network.json resource not found.");
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Path tempFile = Files.createTempFile("sample_network", ".json");
            Files.writeString(tempFile, content, StandardCharsets.UTF_8);
            try {
                new JsonDataManager().importNetwork(tempFile, db);
            } finally {
                Files.deleteIfExists(tempFile);
            }
        }
    }
}
