package de.mrsimplejs.simplegeo;

import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.ProxyServer;

final class SimpleGeoCommands {
    private SimpleGeoCommands() {
    }

    static void register(ProxyServer proxy, SimpleGeoPlugin plugin) {
        register(proxy, "geo-allow", new GeoAllow(plugin), "geoallow");
        register(proxy, "bot-allow", new BotAllow(plugin), "botallow");
        register(proxy, "maintenance-eta", new MaintenanceEta(plugin), "maintenanceeta");
        register(proxy, "maintenance-allow", new MaintenanceAllow(plugin), "maintenanceallow");
    }

    private static void register(ProxyServer proxy, String name, SimpleCommand command, String... aliases) {
        CommandMeta meta = proxy.getCommandManager().metaBuilder(name).aliases(aliases).build();
        proxy.getCommandManager().register(meta, command);
    }

    private record GeoAllow(SimpleGeoPlugin plugin) implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!plugin.canGeoAllow(source)) {
                plugin.send(source, "command.no-permission");
                return;
            }
            Integer id = parseId(invocation.arguments(), source, plugin, "command.geoallow-usage");
            if (id == null) return;
            GeoBlock block = plugin.allowGeoBlock(id);
            if (block == null) {
                plugin.send(source, "command.geo-not-found");
                return;
            }
            plugin.send(source, "command.geo-allowed", "ip", block.ip(), "id", String.valueOf(id));
        }
    }

    private record BotAllow(SimpleGeoPlugin plugin) implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!plugin.canBotAllow(source)) {
                plugin.send(source, "command.no-permission");
                return;
            }
            Integer id = parseId(invocation.arguments(), source, plugin, "command.botallow-usage");
            if (id == null) return;
            BotBan ban = plugin.allowBotBan(id);
            if (ban == null) {
                plugin.send(source, "command.bot-not-found");
                return;
            }
            plugin.send(source, "command.bot-allowed", "ip", ban.ip(), "id", String.valueOf(id));
        }
    }

    private record MaintenanceEta(SimpleGeoPlugin plugin) implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            CommandSource source = invocation.source();
            if (!plugin.isStaff(source)) {
                plugin.send(source, "command.no-permission");
                return;
            }
            String eta = String.join(" ", invocation.arguments()).trim();
            if (eta.isBlank()) {
                plugin.send(source, invocation.arguments().length == 0 ? "command.eta-usage" : "command.eta-required");
                return;
            }
            plugin.updateMaintenanceEta(eta);
            plugin.send(source, "command.eta-set", "eta", eta);
        }
    }

    private record MaintenanceAllow(SimpleGeoPlugin plugin) implements SimpleCommand {
        @Override
        public void execute(Invocation invocation) {
            if (!plugin.isStaff(invocation.source())) {
                plugin.send(invocation.source(), "command.no-permission");
                return;
            }
            plugin.send(invocation.source(), "command.maintenance-access");
        }
    }

    private static Integer parseId(
        String[] arguments, CommandSource source, SimpleGeoPlugin plugin, String usageKey
    ) {
        if (arguments.length == 0) {
            plugin.send(source, usageKey);
            return null;
        }
        try {
            return Integer.parseInt(arguments[0]);
        } catch (NumberFormatException e) {
            plugin.send(source, "command.invalid-id");
            return null;
        }
    }
}
