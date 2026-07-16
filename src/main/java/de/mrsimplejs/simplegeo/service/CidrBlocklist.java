package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

final class CidrBlocklist {
    private final Logger logger;
    private final List<IpRange> ranges = new ArrayList<>();

    CidrBlocklist(Logger logger) {
        this.logger = logger;
    }

    void load(String raw) {
        ranges.clear();
        if (raw == null || raw.isBlank()) return;
        for (String entry : raw.split(",")) {
            if (entry == null || entry.isBlank()) continue;
            IpRange range = NetworkUtils.parseCidr(entry.trim());
            if (range == null) logger.warn("Invalid CIDR entry in blocked-cidrs: {}.", entry.trim());
            else ranges.add(range);
        }
    }

    boolean contains(String ip) {
        if (ip == null || ip.isBlank() || ranges.isEmpty()) return false;
        BigInteger value = NetworkUtils.resolveIpValue(ip);
        if (value == null) return false;
        for (IpRange range : ranges) {
            if (range.contains(value)) return true;
        }
        return false;
    }

    String configuredRanges() {
        return String.join(",", ranges.stream().map(IpRange::cidr).toList());
    }
}
