package de.mrsimplejs.simplegeo;

import java.util.HashMap;
import java.util.Map;

final class GeoBlockRepository {
    private final Map<Integer, GeoBlock> byId = new HashMap<>();
    private final Map<String, Integer> byIp = new HashMap<>();
    private int nextId = 1;

    GeoBlock find(String ip) {
        Integer id = byIp.get(ip);
        if (id == null) return null;
        GeoBlock block = byId.get(id);
        if (block == null) byIp.remove(ip);
        return block;
    }

    GeoBlock create(String ip, String name, String countryCode) {
        GeoBlock block = new GeoBlock(nextId++, ip, name, countryCode, System.currentTimeMillis());
        byId.put(block.id(), block); byIp.put(ip, block.id());
        return block;
    }

    GeoBlock remove(int id) {
        GeoBlock block = byId.remove(id);
        if (block != null) byIp.remove(block.ip());
        return block;
    }
}
