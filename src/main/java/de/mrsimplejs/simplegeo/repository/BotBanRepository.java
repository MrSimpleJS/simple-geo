package de.mrsimplejs.simplegeo;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

final class BotBanRepository {
    private static final long BAN_DURATION_MS = TimeUnit.HOURS.toMillis(1);
    private final Path file;
    private final PropertyStore store;
    private final Map<Integer, BotBan> byId = new HashMap<>();
    private final Map<String, Integer> byIp = new HashMap<>();
    private int nextId = 1;

    BotBanRepository(Path file, PropertyStore store) {
        this.file = file;
        this.store = store;
    }

    void load() {
        byId.clear(); byIp.clear(); nextId = 1;
        Properties properties = store.read(file, "botbans.properties");
        if (properties == null) return;
        long now = System.currentTimeMillis();
        int maxId = 0;
        boolean changed = false;
        for (String key : properties.stringPropertyNames()) {
            try {
                int id = Integer.parseInt(key.trim());
                BotBan ban = parse(properties.getProperty(key), id);
                if (ban == null || ban.expiresAt() <= now) { changed = true; continue; }
                byId.put(id, ban); byIp.put(ban.ip(), id); maxId = Math.max(maxId, id);
            } catch (NumberFormatException e) { changed = true; }
        }
        nextId = maxId + 1;
        if (changed) save();
    }

    BotBan create(String ip, String name, long now) {
        BotBan ban = new BotBan(nextId++, ip, name, now, now + BAN_DURATION_MS);
        byId.put(ban.id(), ban); byIp.put(ip, ban.id()); save();
        return ban;
    }

    BotBan active(String ip, long now) {
        Integer id = byIp.get(ip);
        if (id == null) return null;
        BotBan ban = byId.get(id);
        if (ban == null || ban.expiresAt() <= now) {
            byId.remove(id); byIp.remove(ip); save(); return null;
        }
        return ban;
    }

    BotBan remove(int id) {
        BotBan ban = byId.remove(id);
        if (ban == null) {
            Properties properties = store.read(file, "botbans.properties");
            if (properties == null) return null;
            ban = parse(properties.getProperty(String.valueOf(id)), id);
            properties.remove(String.valueOf(id));
            store.write(file, properties, "botbans.properties");
        } else save();
        if (ban != null) byIp.remove(ban.ip());
        return ban;
    }

    void save() {
        Properties properties = new Properties();
        byId.values().forEach(ban -> properties.setProperty(String.valueOf(ban.id()), format(ban)));
        store.write(file, properties, "botbans.properties");
    }

    private static String format(BotBan ban) {
        return ban.id() + "|" + ban.ip() + "|" + ban.name().replace("|", "_") + "|"
            + ban.createdAt() + "|" + ban.expiresAt();
    }

    private static BotBan parse(String value, int id) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split("\\|", -1);
        int offset = parts.length >= 5 && number(parts[0]) != null ? 1 : 0;
        if (parts.length - offset < 4) return null;
        Long created = number(parts[offset + 2]);
        Long expires = number(parts[offset + 3]);
        String ip = parts[offset].trim();
        return ip.isBlank() || created == null || expires == null
            ? null : new BotBan(id, ip, parts[offset + 1].trim(), created, expires);
    }

    private static Long number(String value) {
        try { return Long.parseLong(value.trim()); } catch (Exception e) { return null; }
    }
}
