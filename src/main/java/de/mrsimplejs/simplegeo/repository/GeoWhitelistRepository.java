package de.mrsimplejs.simplegeo;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

final class GeoWhitelistRepository {
    private final Path file;
    private final PropertyStore store;
    private final Set<String> addresses = new HashSet<>();

    GeoWhitelistRepository(Path file, PropertyStore store) {
        this.file = file;
        this.store = store;
    }

    void load() {
        addresses.clear();
        Properties properties = store.read(file, "geowhitelist.properties");
        if (properties != null) properties.stringPropertyNames().stream()
            .filter(key -> !key.isBlank()).map(String::trim).forEach(addresses::add);
    }

    boolean contains(String ip) { return addresses.contains(ip); }

    void add(String ip) {
        addresses.add(ip);
        Properties properties = new Properties();
        addresses.forEach(address -> properties.setProperty(address, "allowed"));
        store.write(file, properties, "geowhitelist.properties");
    }
}
