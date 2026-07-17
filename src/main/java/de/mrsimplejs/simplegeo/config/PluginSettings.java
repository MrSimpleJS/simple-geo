package de.mrsimplejs.simplegeo;

import org.slf4j.Logger;

import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

record PluginSettings(
    Set<String> allowedCountries,
    Set<String> blockedIsps,
    String blockedCidrs,
    String ip2ProxyFile,
    String language,
    boolean ipWhoisEnabled,
    int ipWhoisTimeoutMs,
    boolean maintenanceEnabled,
    String maintenanceEta,
    String botbanSyncUrl,
    String botbanSyncSecret,
    long botbanSyncIntervalSeconds,
    String botbanUnbanSyncUrl,
    String botbanUnbanSyncSecret,
    long botbanUnbanSyncIntervalSeconds,
    Set<String> permissionsAllow,
    Set<String> permissionsBotAllow,
    Set<String> permissionsNotify,
    Set<String> permissionsBypass,
    Set<String> permissionsAntiBotBypass,
    Set<String> permissionsAdmin,
    Set<String> permissionsMod,
    Set<String> permissionsCadmin,
    String ansiReset,
    String ansiRed,
    String ansiGreen,
    String ansiYellow
) {
    private static final String FILE_NAME = "config.properties";

    static PluginSettings load(Path dataDirectory, Logger logger) {
        Path file = dataDirectory.resolve(FILE_NAME);
        Properties properties = ConfigFile.load(file, FILE_NAME, logger);
        String countries = firstNonBlank(properties.getProperty("allowed-countries"),
            properties.getProperty("allowed-country"), "");
        PluginSettings settings = new PluginSettings(
            split(countries, ",", true),
            split(properties.getProperty("blocked-isps", ""), ";", false),
            properties.getProperty("blocked-cidrs", "").trim(),
            value(properties, "ip2proxy-file", "IP2PROXY-LITE-PX2.CSV"),
            value(properties, "language", "en").toLowerCase(Locale.ROOT),
            Boolean.parseBoolean(properties.getProperty("ipwhois-enabled", "true").trim()),
            Math.max(100, integer(properties.getProperty("ipwhois-timeout-ms"), 1500)),
            Boolean.parseBoolean(properties.getProperty("maintenance", "false").trim()),
            value(properties, "maintenance-eta", "unknown"),
            value(properties, "botban-sync-url", ""),
            value(properties, "botban-sync-secret", ""),
            Math.max(0, number(properties.getProperty("botban-sync-interval-seconds"), 60)),
            value(properties, "botban-unban-sync-url", ""),
            value(properties, "botban-unban-sync-secret", ""),
            Math.max(0, number(properties.getProperty("botban-unban-sync-interval-seconds"), 30)),
            permissionList(properties, "permission-allow", "simplegeo.allow"),
            permissionList(properties, "permission-bot-allow", "simplegeo.botallow"),
            permissionList(properties, "permission-notify", "simplegeo.notify"),
            permissionList(properties, "permission-bypass", "simplegeo.bypass"),
            permissionList(properties, "permission-antibot-bypass", "antibot.bypass"),
            permissionList(properties, "permission-admin", "group.admin"),
            permissionList(properties, "permission-mod", "group.mod"),
            permissionList(properties, "permission-cadmin", "group.cadmin"),
            value(properties, "ansi-reset", "\u001B[0m"),
            value(properties, "ansi-red", "\u001B[31m"),
            value(properties, "ansi-green", "\u001B[32m"),
            value(properties, "ansi-yellow", "\u001B[33m")
        );
        settings.writeNormalized(file, properties, logger);
        return settings;
    }

    private void writeNormalized(Path file, Properties properties, Logger logger) {
        properties.setProperty("allowed-countries", String.join(",", allowedCountries));
        properties.setProperty("blocked-isps", String.join(";", blockedIsps));
        properties.setProperty("blocked-cidrs", blockedCidrs);
        properties.setProperty("ip2proxy-file", ip2ProxyFile);
        properties.setProperty("language", language);
        properties.setProperty("ipwhois-enabled", String.valueOf(ipWhoisEnabled));
        properties.setProperty("ipwhois-timeout-ms", String.valueOf(ipWhoisTimeoutMs));
        properties.setProperty("maintenance", String.valueOf(maintenanceEnabled));
        properties.setProperty("maintenance-eta", maintenanceEta);
        properties.setProperty("botban-sync-url", botbanSyncUrl);
        properties.setProperty("botban-sync-secret", botbanSyncSecret);
        properties.setProperty("botban-sync-interval-seconds", String.valueOf(botbanSyncIntervalSeconds));
        properties.setProperty("botban-unban-sync-url", botbanUnbanSyncUrl);
        properties.setProperty("botban-unban-sync-secret", botbanUnbanSyncSecret);
        properties.setProperty("botban-unban-sync-interval-seconds", String.valueOf(botbanUnbanSyncIntervalSeconds));
        properties.setProperty("permission-allow", String.join(",", permissionsAllow));
        properties.setProperty("permission-bot-allow", String.join(",", permissionsBotAllow));
        properties.setProperty("permission-notify", String.join(",", permissionsNotify));
        properties.setProperty("permission-bypass", String.join(",", permissionsBypass));
        properties.setProperty("permission-antibot-bypass", String.join(",", permissionsAntiBotBypass));
        properties.setProperty("permission-admin", String.join(",", permissionsAdmin));
        properties.setProperty("permission-mod", String.join(",", permissionsMod));
        properties.setProperty("permission-cadmin", String.join(",", permissionsCadmin));
        properties.setProperty("ansi-reset", ansiReset);
        properties.setProperty("ansi-red", ansiRed);
        properties.setProperty("ansi-green", ansiGreen);
        properties.setProperty("ansi-yellow", ansiYellow);
        ConfigFile.save(file, properties, logger);
    }

    private static Set<String> split(String raw, String delimiter, boolean uppercase) {
        Set<String> values = new LinkedHashSet<>();
        if (raw == null) return values;
        for (String entry : raw.split(java.util.regex.Pattern.quote(delimiter))) {
            if (!entry.isBlank()) values.add(uppercase ? entry.trim().toUpperCase(Locale.ROOT) : entry.trim());
        }
        return values;
    }

    private static String value(Properties properties, String key, String fallback) {
        String value = properties.getProperty(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static Set<String> permissionList(Properties properties, String key, String fallback) {
        Set<String> permissions = split(value(properties, key, fallback), ",", false);
        return permissions.isEmpty() ? Set.of(fallback) : permissions;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) return first.trim();
        if (second != null && !second.isBlank()) return second.trim();
        return fallback;
    }

    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value.trim()); } catch (Exception e) { return fallback; }
    }

    private static long number(String value, long fallback) {
        try { return Long.parseLong(value.trim()); } catch (Exception e) { return fallback; }
    }
}
