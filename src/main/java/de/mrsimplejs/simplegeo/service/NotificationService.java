package de.mrsimplejs.simplegeo;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.function.Predicate;

final class NotificationService {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private static final int MOTD_CENTER_WIDTH = 54;

    private final ProxyServer proxy;
    private final LanguageManager language;
    private final Predicate<Player> staffFilter;

    NotificationService(ProxyServer proxy, LanguageManager language, Predicate<Player> staffFilter) {
        this.proxy = proxy;
        this.language = language;
        this.staffFilter = staffFilter;
    }

    Component botBanKick(BotBan ban, long now) {
        long minutes = Math.max(0L, ban.expiresAt() - now) / 60_000L;
        long hours = minutes / 60L;
        String duration = hours > 0 ? hours + "h " + (minutes % 60L) + "m" : minutes + "m";
        return legacy("kick.bot-ban", "duration", duration, "id", String.valueOf(ban.id()));
    }

    Component vpnKick(BotBan ban) {
        return legacy("kick.vpn", "id", String.valueOf(ban.id()));
    }

    Component geoKick(GeoBlock block) {
        return legacy("kick.geo", "id", String.valueOf(block.id()));
    }

    Component maintenanceKick(String eta) {
        return legacy("kick.maintenance", "eta", eta);
    }

    Component maintenanceMotd(String eta) {
        String text = language.message("motd.maintenance", "eta", eta);
        return LEGACY.deserialize("&c&l" + NetworkUtils.centerLegacyText(text, MOTD_CENTER_WIDTH));
    }

    void botBanAttempt(BotBan ban, String message) {
        if (ban == null || message == null || message.isBlank()) return;
        Component base = LEGACY.deserialize(message)
            .hoverEvent(HoverEvent.showText(Component.text(language.message("staff.click-whitelist"))));
        Component action = legacy("staff.whitelist-action")
            .clickEvent(ClickEvent.runCommand("/bot-allow " + ban.id()))
            .hoverEvent(HoverEvent.showText(Component.text(language.message("staff.whitelist-hover",
                "name", ban.name(), "id", String.valueOf(ban.id())))));
        broadcast(base.append(action));
    }

    void existingGeoBlock(GeoBlock block, String name) {
        if (block == null || name == null || name.isBlank()) return;
        Component base = legacy("staff.geo-already-blocked", "name", name, "id", String.valueOf(block.id()));
        broadcast(base.append(geoAction(block.id(), language.message("staff.whitelist-hover",
            "name", name, "id", String.valueOf(block.id())))));
    }

    void newGeoBlock(GeoBlock block) {
        Component base = legacy("staff.geo-blocked", "name", block.name(), "id", String.valueOf(block.id()));
        broadcast(base.append(geoAction(block.id(), language.message("staff.whitelist-ip-hover",
            "ip", block.ip(), "id", String.valueOf(block.id())))));
    }

    void maintenanceAttempt(String name) {
        if (name == null || name.isBlank()) return;
        Component message = legacy("staff.maintenance-attempt", "name", name)
            .hoverEvent(HoverEvent.showText(Component.text(language.message("staff.maintenance-hover", "name", name))));
        broadcast(message);
    }

    private Component geoAction(int id, String hover) {
        return legacy("staff.whitelist-action")
            .clickEvent(ClickEvent.runCommand("/geo-allow " + id))
            .hoverEvent(HoverEvent.showText(Component.text(hover)));
    }

    private Component legacy(String key, String... replacements) {
        return LEGACY.deserialize(language.message(key, replacements));
    }

    private void broadcast(Component message) {
        for (Player player : proxy.getAllPlayers()) {
            if (staffFilter.test(player)) player.sendMessage(message);
        }
    }
}
