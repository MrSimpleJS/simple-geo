package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

final class Ip2ProxyService {
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_GREEN = "\u001B[32m";
    private final Path dataDirectory;
    private final Logger logger;
    private final List<Ip2ProxyRange> ranges = new ArrayList<>();

    Ip2ProxyService(Path dataDirectory, Logger logger) {
        this.dataDirectory = dataDirectory;
        this.logger = logger;
    }

    void load(String configuredFileName) {
        ranges.clear();
        Path file = resolveFile(configuredFileName);
        if (file == null) {
            logger.warn("IP2Proxy LITE database not found ({}).", configuredFileName);
            return;
        }
        int badLines = 0;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                List<String> fields = NetworkUtils.parseCsvLine(line);
                if (fields.isEmpty() || fields.getFirst().trim().equalsIgnoreCase("ip_from")) continue;
                if (fields.size() < 3) { badLines++; continue; }
                BigInteger start = NetworkUtils.parseBigInteger(fields.get(0));
                BigInteger end = NetworkUtils.parseBigInteger(fields.get(1));
                if (start == null || end == null) { badLines++; continue; }
                String type = fields.get(2) == null ? "" : fields.get(2).trim();
                String isp = fields.size() > 7 && fields.get(7) != null ? fields.get(7).trim() : "";
                ranges.add(new Ip2ProxyRange(start, end, type, isp));
            }
            ranges.sort(Comparator.comparing(Ip2ProxyRange::start));
            logger.info(ANSI_GREEN + "IP2Proxy LITE database loaded from {} ({} ranges)." + ANSI_RESET,
                file, ranges.size());
            if (badLines > 0) logger.warn("IP2Proxy LITE skipped {} invalid lines.", badLines);
        } catch (IOException e) {
            logger.warn("Could not load IP2Proxy LITE database.", e);
        }
    }

    String proxyType(String ip) {
        Ip2ProxyRange range = find(ip);
        return range == null ? null : range.proxyType();
    }

    String isp(String ip) {
        Ip2ProxyRange range = find(ip);
        return range == null ? null : range.isp();
    }

    private Ip2ProxyRange find(String ip) {
        BigInteger value = NetworkUtils.resolveIpValue(ip);
        if (value == null || ranges.isEmpty()) return null;
        int low = 0;
        int high = ranges.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            Ip2ProxyRange range = ranges.get(mid);
            if (value.compareTo(range.start()) < 0) high = mid - 1;
            else if (value.compareTo(range.end()) > 0) low = mid + 1;
            else return range;
        }
        return null;
    }

    private Path resolveFile(String configuredFileName) {
        Path configured = dataDirectory.resolve(configuredFileName);
        if (Files.isRegularFile(configured)) return configured;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dataDirectory)) {
            for (Path path : stream) {
                String name = path.getFileName().toString().toUpperCase(Locale.ROOT);
                if (name.startsWith("IP2PROXY-LITE") && name.endsWith(".CSV")) return path;
            }
        } catch (IOException e) {
            logger.warn("Could not scan the data directory for IP2Proxy LITE.", e);
        }
        return null;
    }
}
