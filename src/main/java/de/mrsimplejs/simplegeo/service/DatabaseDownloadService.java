package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;

final class DatabaseDownloadService {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private final Path dataDirectory;
    private final Logger logger;

    DatabaseDownloadService(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    void downloadMissing() {
        Properties config = ConfigFile.load(dataDirectory.resolve("config.properties"), "config.properties", logger);
        if (!Boolean.parseBoolean(config.getProperty("database-auto-download", "true"))) {
            return;
        }
        extractBundled("/databases/GeoLite2-Country.mmdb", dataDirectory.resolve("GeoLite2-Country.mmdb"));
        String ip2ProxyFile = config.getProperty("ip2proxy-file", "IP2PROXY-LITE-PX2.CSV").trim();
        extractBundled("/databases/IP2PROXY-LITE-PX2.CSV", dataDirectory.resolve(ip2ProxyFile));
    }

    private void extractBundled(String resource, Path target) {
        if (Files.isRegularFile(target)) {
            return;
        }
        String displayName = target.getFileName().toString();
        Path temporary = target.resolveSibling(displayName + ".tmp");
        try (InputStream input = SimpleGeoPlugin.class.getResourceAsStream(resource)) {
            if (input == null) {
                logger.warn("Bundled database {} is missing from the plugin JAR.", displayName);
                return;
            }
            Files.createDirectories(dataDirectory);
            logger.info(ANSI_CYAN + "Extracting bundled database {}..." + ANSI_RESET, displayName);
            long copied = 0L;
            long nextProgress = 10L * 1024L * 1024L;
            try (var output = Files.newOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    copied += read;
                    if (copied >= nextProgress) {
                        logger.info(ANSI_CYAN + "Extracting {}... {} MB copied" + ANSI_RESET,
                            displayName, copied / 1024L / 1024L);
                        nextProgress += 10L * 1024L * 1024L;
                    }
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            logger.info(ANSI_GREEN + "Extracting {} complete ({} MB)." + ANSI_RESET,
                displayName, copied / 1024L / 1024L);
        } catch (IOException e) {
            logger.warn("Could not extract bundled database {}.", displayName, e);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
            }
        }
    }
}
