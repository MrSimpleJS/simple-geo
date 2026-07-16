package de.mrsimplejs.simplegeo;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.AddressNotFoundException;
import com.maxmind.geoip2.model.CountryResponse;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

final class GeoIpService implements AutoCloseable {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private final Logger logger;
    private DatabaseReader database;

    GeoIpService(Logger logger) {
        this.logger = logger;
    }

    void load(Path databaseFile) {
        close();
        if (!Files.isRegularFile(databaseFile)) {
            logger.warn("GeoLite2 database not found at {}.", databaseFile);
            return;
        }
        try (InputStream input = Files.newInputStream(databaseFile)) {
            database = new DatabaseReader.Builder(input).build();
            logger.info(ANSI_GREEN + "GeoLite2 database loaded from {}." + ANSI_RESET, databaseFile);
        } catch (IOException e) {
            logger.warn("Could not load GeoLite2 database.", e);
        }
    }

    boolean isAvailable() {
        return database != null;
    }

    String countryCode(String ip) {
        if (database == null || ip == null || ip.isBlank()) return null;
        try {
            CountryResponse response = database.country(InetAddress.getByName(ip));
            return response == null || response.country() == null ? null : response.country().isoCode();
        } catch (AddressNotFoundException e) {
            return null;
        } catch (Exception e) {
            logger.warn("GeoIP lookup failed for {}.", ip, e);
            return null;
        }
    }

    @Override
    public void close() {
        if (database == null) return;
        try {
            database.close();
        } catch (IOException e) {
            logger.warn("Could not close GeoLite2 database.", e);
        } finally {
            database = null;
        }
    }
}
