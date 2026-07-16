package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

final class ConfigFile {
    private ConfigFile() {
    }

    static Properties load(Path file, String resourceName, Logger logger) {
        Properties merged = new Properties();
        try (InputStream input = SimpleGeoPlugin.class.getResourceAsStream("/" + resourceName)) {
            if (input == null) {
                logger.warn("Bundled {} is missing.", resourceName);
            } else {
                merged.load(input);
            }
        } catch (IOException e) {
            logger.warn("Could not load bundled {}.", resourceName, e);
        }

        if (Files.isRegularFile(file)) {
            Properties overrides = new Properties();
            try (InputStream input = Files.newInputStream(file)) {
                overrides.load(input);
                merged.putAll(overrides);
            } catch (IOException e) {
                logger.warn("Could not read {}.", file, e);
            }
        }
        return merged;
    }

    static void save(Path file, Properties properties, Logger logger) {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "Simple-GEO config");
            }
        } catch (IOException e) {
            logger.warn("Could not write {}.", file, e);
        }
    }
}
