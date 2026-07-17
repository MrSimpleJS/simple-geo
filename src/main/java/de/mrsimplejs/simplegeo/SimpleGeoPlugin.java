package de.mrsimplejs.simplegeo;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Plugin(
    id = "simple-geo",
    name = "Simple-GEO",
    version = "1.0.0",
    description = "GeoIP, VPN/proxy and anti-bot protection for Velocity.",
    authors = {"MrSimpleJS"}
)
public final class SimpleGeoPlugin {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private static final String CONFIG_FILE = "config.properties";
    private static final String WHITELIST_FILE = "geowhitelist.properties";
    private static final String BOTBAN_FILE = "botbans.properties";
    private static final String ALLOWED_PLAYERS_FILE = "allowed-players.properties";
    private static final String GEO_DB_FILE = "GeoLite2-Country.mmdb";
    private static final String IP2PROXY_DB_FILE = "IP2PROXY-LITE-PX2.CSV";
    private static final long SYNC_NETWORK_BACKOFF_MS = TimeUnit.MINUTES.toMillis(10);

    private static final String DEFAULT_MAINTENANCE_ETA = "unknown";


    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final PropertyStore propertyStore;

    private Path geoDatabaseFile;

    private String ip2proxyFileName = IP2PROXY_DB_FILE;
    private String language = "en";
    private final LanguageManager languageManager;
    private boolean ipWhoisEnabled = true;
    private int ipWhoisTimeoutMs;
    private Set<String> permissionsAllow;
    private Set<String> permissionsBotAllow;
    private Set<String> permissionsNotify;
    private Set<String> permissionsBypass;
    private Set<String> permissionsAntiBotBypass;
    private Set<String> permissionsAdmin;
    private Set<String> permissionsMod;
    private Set<String> permissionsCadmin;
    private String ansiReset;
    private String ansiRed;
    private String ansiGreen;
    private String ansiYellow;

    private final Set<String> allowedCountries = new HashSet<>();
    private final Set<String> blockedIsps = new HashSet<>();
    private final GeoWhitelistRepository geoWhitelistRepository;

    private final GeoBlockRepository geoBlockRepository = new GeoBlockRepository();

    private final AntiBotTracker antiBotTracker = new AntiBotTracker();

    private final BotBanRepository botBanRepository;

    private final Ip2ProxyService ip2ProxyService;
    private final GeoIpService geoIpService;
    private final CidrBlocklist cidrBlocklist;
    private final IspLookupService ispLookupService;
    private final NotificationService notificationService;
    private final AllowedPlayerService allowedPlayerService;

    private boolean maintenanceEnabled = false;
    private String maintenanceEta = DEFAULT_MAINTENANCE_ETA;
    private PluginSettings settings;
    private final BotBanSyncService botBanSyncService;
    private final DatabaseDownloadService databaseDownloadService;

    @Inject
    public SimpleGeoPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.languageManager = new LanguageManager(dataDirectory, logger);
        this.propertyStore = new PropertyStore(dataDirectory, logger);
        this.ip2ProxyService = new Ip2ProxyService(dataDirectory, logger);
        this.geoIpService = new GeoIpService(logger);
        this.cidrBlocklist = new CidrBlocklist(logger);
        this.ispLookupService = new IspLookupService(proxy, this, logger);
        this.geoWhitelistRepository = new GeoWhitelistRepository(dataDirectory.resolve(WHITELIST_FILE), propertyStore);
        this.botBanRepository = new BotBanRepository(dataDirectory.resolve(BOTBAN_FILE), propertyStore);
        this.notificationService = new NotificationService(proxy, languageManager, this::isStaffNotify);
        this.botBanSyncService = new BotBanSyncService(proxy, this, logger,
            dataDirectory.resolve(BOTBAN_FILE), botBanRepository);
        this.allowedPlayerService = new AllowedPlayerService(dataDirectory.resolve(ALLOWED_PLAYERS_FILE),
            propertyStore, ip2ProxyService, ispLookupService);
        this.databaseDownloadService = new DatabaseDownloadService(dataDirectory, logger);
    }

    @Subscribe
    public void onProxyInitialize(com.velocitypowered.api.event.proxy.ProxyInitializeEvent event) {
        geoDatabaseFile = dataDirectory.resolve(GEO_DB_FILE);

        loadConfig();
        language = languageManager.load(language);
        databaseDownloadService.downloadMissing();
        geoIpService.load(geoDatabaseFile);
        ip2ProxyService.load(ip2proxyFileName);
        geoWhitelistRepository.load();
        botBanRepository.load();
        botBanSyncService.start(settings);

        SimpleGeoCommands.register(proxy, this);
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        if (!maintenanceEnabled) {
            return;
        }
        ServerPing ping = event.getPing();
        Component motd = notificationService.maintenanceMotd(maintenanceEta);
        event.setPing(ping.asBuilder().description(motd).build());
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        if (checkMaintenance(player)) {
            return;
        }
        if (checkVpnProxy(player)) {
            return;
        }
        if (checkGeoBlock(player)) {
            return;
        }
        if (checkAntiBot(player)) {
            return;
        }
        allowedPlayerService.record(player, resolvePlayerIp(player));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        botBanSyncService.close();
        geoIpService.close();
    }


    private void loadConfig() {
        settings = PluginSettings.load(dataDirectory, logger);
        allowedCountries.clear();
        allowedCountries.addAll(settings.allowedCountries());
        blockedIsps.clear();
        blockedIsps.addAll(settings.blockedIsps());
        cidrBlocklist.load(settings.blockedCidrs());
        ip2proxyFileName = settings.ip2ProxyFile();
        language = settings.language();
        ipWhoisEnabled = settings.ipWhoisEnabled();
        ipWhoisTimeoutMs = settings.ipWhoisTimeoutMs();
        ispLookupService.configure(ipWhoisEnabled, ipWhoisTimeoutMs);
        maintenanceEnabled = settings.maintenanceEnabled();
        maintenanceEta = settings.maintenanceEta();
        permissionsAllow = settings.permissionsAllow();
        permissionsBotAllow = settings.permissionsBotAllow();
        permissionsNotify = settings.permissionsNotify();
        permissionsBypass = settings.permissionsBypass();
        permissionsAntiBotBypass = settings.permissionsAntiBotBypass();
        permissionsAdmin = settings.permissionsAdmin();
        permissionsMod = settings.permissionsMod();
        permissionsCadmin = settings.permissionsCadmin();
        ansiReset = settings.ansiReset();
        ansiRed = settings.ansiRed();
        ansiGreen = settings.ansiGreen();
        ansiYellow = settings.ansiYellow();
    }

    private String tr(String key, String... replacements) {
        return languageManager.message(key, replacements);
    }

    private boolean checkGeoBlock(Player player) {
        if (player == null || hasAnyPermission(player, permissionsBypass)) {
            return false;
        }
        String ip = resolvePlayerIp(player);
        if (ip == null || ip.isBlank()) {
            return false;
        }
        if (geoWhitelistRepository.contains(ip) || !geoIpService.isAvailable()) {
            return false;
        }

        GeoBlock activeBlock = geoBlockRepository.find(ip);
        if (activeBlock != null) {
            notificationService.existingGeoBlock(activeBlock, player.getUsername());
            player.disconnect(notificationService.geoKick(activeBlock));
            return true;
        }

        String countryCode = geoIpService.countryCode(ip);
        if (countryCode == null || allowedCountries.contains(countryCode.toUpperCase(Locale.ROOT))) {
            return false;
        }

        GeoBlock block = geoBlockRepository.create(ip, player.getUsername(), countryCode);
        notificationService.newGeoBlock(block);
        player.disconnect(notificationService.geoKick(block));
        return true;
    }

    private boolean checkVpnProxy(Player player) {
        if (player == null) {
            return false;
        }

        String ip = resolvePlayerIp(player);
        if (ip == null || ip.isBlank()) {
            return false;
        }

        String proxyType = ip2ProxyService.proxyType(ip);
        String isp = ip2ProxyService.isp(ip);
        if (isp == null || isp.isBlank()) {
            isp = ispLookupService.lookup(ip);
        }
        boolean ispBlocked = isBlockedIsp(isp);
        boolean ipRangeBlocked = cidrBlocklist.contains(ip);
        boolean proxyBlocked = isProxyTypeBlocked(proxyType) || ispBlocked || ipRangeBlocked;
        String proxyLabel = proxyType == null || proxyType.isBlank() ? "NON" : proxyType;
        String ispLabel = isp == null || isp.isBlank() ? "-" : isp;
        String countryCode = geoIpService.countryCode(ip);
        String countryLabel = countryCode == null || countryCode.isBlank() ? "-" : countryCode;

        logger.info((proxyBlocked ? ansiRed : ansiGreen) + "{} ({} / {} / ISP {} - {}) is connecting" + ansiReset,
            player.getUsername(), countryLabel, proxyLabel, ispLabel, ip);

        if (!ispBlocked && (hasAnyPermission(player, permissionsAntiBotBypass)
            || hasAnyPermission(player, permissionsBypass))) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (handleExistingBotBan(player, ip, now)) {
            return true;
        }

        if (proxyBlocked) {
            BotBan ban = botBanRepository.create(ip, player.getUsername(), now);
            logger.info(ansiRed + "VPN/proxy block: {} was blocked (IP {}, type {}, ISP {}, CIDR {}, ID {})." + ansiReset,
                player.getUsername(), ip, proxyType == null ? "-" : proxyType, ispLabel, ipRangeBlocked, ban.id());
            notificationService.botBanAttempt(ban, tr("staff.vpn-attempt", "name", player.getUsername())
                + (proxyType == null || proxyType.isBlank() ? "" : " &8(" + proxyType + ")")
                + (isBlockedIsp(isp) ? " &8(ISP: " + ispLabel + ")" : "")
                + (ipRangeBlocked ? tr("staff.cidr-label") : ""));
            player.disconnect(notificationService.vpnKick(ban));
            return true;
        }
        return false;
    }

    private boolean checkAntiBot(Player player) {
        if (player == null) {
            return false;
        }
        if (hasAnyPermission(player, permissionsAntiBotBypass)
            || hasAnyPermission(player, permissionsBypass)) {
            return false;
        }

        String ip = resolvePlayerIp(player);
        if (ip == null || ip.isBlank()) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (handleExistingBotBan(player, ip, now)) {
            return true;
        }

        AntiBotTracker.Result result = antiBotTracker.record(ip, player.getUsername(), now);
        if (result == AntiBotTracker.Result.MULTIPLE_NAMES) {
            BotBan ban = botBanRepository.create(ip, player.getUsername(), now);
            notificationService.botBanAttempt(ban, tr("staff.multiple-names", "name", player.getUsername()));
            player.disconnect(notificationService.botBanKick(ban, now));
            return true;
        }
        if (result == AntiBotTracker.Result.TOO_MANY_JOINS) {
            BotBan ban = botBanRepository.create(ip, player.getUsername(), now);
            notificationService.botBanAttempt(ban, tr("staff.too-many-joins", "name", player.getUsername()));
            player.disconnect(notificationService.botBanKick(ban, now));
            return true;
        }
        return false;
    }

    private boolean checkMaintenance(Player player) {
        if (!maintenanceEnabled || player == null) {
            return false;
        }
        if (isModOrAdmin(player)) {
            return false;
        }
        notificationService.maintenanceAttempt(player.getUsername());
        player.disconnect(notificationService.maintenanceKick(maintenanceEta));
        return true;
    }

    private boolean handleExistingBotBan(Player player, String ip, long now) {
        BotBan activeBan = botBanRepository.active(ip, now);
        if (activeBan == null) {
            return false;
        }
        botBanRepository.save();
        notificationService.botBanAttempt(activeBan, tr("staff.already-banned", "name", player.getUsername()));
        player.disconnect(notificationService.botBanKick(activeBan, now));
        return true;
    }

    private String resolvePlayerIp(Player player) {
        InetSocketAddress address = player.getRemoteAddress();
        if (address == null) {
            return null;
        }
        InetAddress inet = address.getAddress();
        if (inet != null) {
            return inet.getHostAddress();
        }
        String host = address.getHostString();
        return host == null || host.isBlank() ? null : host;
    }

    private void sendLegacy(CommandSource source, String message) {
        source.sendMessage(LEGACY.deserialize(message));
    }

    private boolean isModOrAdmin(CommandSource source) {
        if (source instanceof Player player) {
            return hasAnyPermission(player, permissionsAdmin)
                || hasAnyPermission(player, permissionsMod)
                || hasAnyPermission(player, permissionsCadmin);
        }
        return false;
    }

    private boolean isStaffNotify(Player player) {
        return hasAnyPermission(player, permissionsNotify) || isModOrAdmin(player);
    }

    private static boolean hasAnyPermission(CommandSource source, Set<String> permissions) {
        return permissions.stream().anyMatch(source::hasPermission);
    }

    private static boolean isProxyTypeBlocked(String proxyType) {
        if (proxyType == null || proxyType.isBlank()) {
            return false;
        }
        String normalized = proxyType.trim().toUpperCase(Locale.ROOT);
        return !normalized.equals("-") && !normalized.equals("NON");
    }

    private boolean isBlockedIsp(String isp) {
        if (isp == null || isp.isBlank()) {
            return false;
        }
        String normalized = isp.trim().toLowerCase(Locale.ROOT);
        for (String blocked : blockedIsps) {
            if (normalized.equals(blocked.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private void saveMaintenanceConfig() {
        Path configFile = dataDirectory.resolve(CONFIG_FILE);
        Properties properties = propertyStore.read(configFile, "config.properties");
        if (properties == null) {
            properties = new Properties();
        }
        properties.setProperty("maintenance", String.valueOf(maintenanceEnabled));
        properties.setProperty("maintenance-eta",
            maintenanceEta == null || maintenanceEta.isBlank() ? DEFAULT_MAINTENANCE_ETA : maintenanceEta);
        propertyStore.write(configFile, properties, "config.properties");
    }

    boolean canGeoAllow(CommandSource source) {
        return hasAnyPermission(source, permissionsAllow) || isModOrAdmin(source);
    }

    boolean canBotAllow(CommandSource source) {
        return hasAnyPermission(source, permissionsBotAllow) || isModOrAdmin(source);
    }

    boolean isStaff(CommandSource source) {
        return isModOrAdmin(source);
    }

    GeoBlock allowGeoBlock(int id) {
        GeoBlock block = geoBlockRepository.remove(id);
        if (block == null) {
            return null;
        }
        geoWhitelistRepository.add(block.ip());
        return block;
    }

    BotBan allowBotBan(int id) {
        return botBanRepository.remove(id);
    }

    void updateMaintenanceEta(String eta) {
        maintenanceEta = eta;
        saveMaintenanceConfig();
    }

    void send(CommandSource source, String key, String... replacements) {
        sendLegacy(source, tr(key, replacements));
    }

}
