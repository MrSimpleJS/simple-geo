package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class PropertyStore {
    private final Path dataDirectory;
    private final Logger logger;

    PropertyStore(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    Properties read(Path file, String label) {
        if (file == null || !Files.exists(file)) return null;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            return properties;
        } catch (IOException e) {
            logger.warn("Could not read {} ({}).", label, file, e);
            return null;
        }
    }

    void write(Path file, Properties properties, String label) {
        if (file == null || properties == null) return;
        Path parent = file.getParent() == null ? dataDirectory : file.getParent();
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(parent);
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Simple-GEO " + label);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            logger.warn("Could not save {} ({}).", label, file, e);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
