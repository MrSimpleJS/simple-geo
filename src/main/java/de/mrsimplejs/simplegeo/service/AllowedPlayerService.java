package de.mrsimplejs.simplegeo;

import com.velocitypowered.api.proxy.Player;

import java.nio.file.Path;
import java.util.Properties;

final class AllowedPlayerService {
    private final Path file;
    private final PropertyStore store;
    private final Ip2ProxyService ip2Proxy;
    private final IspLookupService ispLookup;
    private final Object lock = new Object();

    AllowedPlayerService(Path file, PropertyStore store, Ip2ProxyService ip2Proxy, IspLookupService ispLookup) {
        this.file = file; this.store = store; this.ip2Proxy = ip2Proxy; this.ispLookup = ispLookup;
    }

    void record(Player player, String ip) {
        if (player == null) return;
        String name = player.getUsername() == null ? "" : player.getUsername().trim();
        String uuid = player.getUniqueId() == null ? "" : player.getUniqueId().toString();
        String key = uuid.isBlank() ? name : uuid;
        if (key.isBlank()) return;
        if (ip == null || ip.isBlank()) { store(key, name, "-", "-", uuid); return; }
        String isp = ip2Proxy.isp(ip);
        if (isp != null && !isp.isBlank()) {
            ispLookup.cache(ip, isp); store(key, name, ip, isp, uuid); return;
        }
        String cached = ispLookup.cached(ip);
        if (cached != null && !cached.isBlank()) { store(key, name, ip, cached, uuid); return; }
        store(key, name, ip, "-", uuid);
        ispLookup.lookupAsync(ip, resolved -> store(key, name, ip, resolved, uuid));
    }

    private void store(String key, String name, String ip, String isp, String uuid) {
        String value = "NAME: " + name + " IP: " + ip + " ISP: " + isp + " UUID: " + uuid;
        synchronized (lock) {
            Properties properties = store.read(file, "allowed-players.properties");
            if (properties == null) properties = new Properties();
            properties.setProperty(key, value);
            store.write(file, properties, "allowed-players.properties");
        }
    }
}
