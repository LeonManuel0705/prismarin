package net.prismarin.core.moderation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.shared.config.CoreConfig;

@Singleton
public final class NetExecListener {

    public static final String CHANNEL = "prismarin:netexec";

    private final PrismarinCorePlugin plugin;
    private final CoreConfig config;
    private final ServerDirectory directory;

    @Inject
    public NetExecListener(PrismarinCorePlugin plugin, CoreConfig config, ServerDirectory directory) {
        this.plugin = plugin;
        this.config = config;
        this.directory = directory;
    }

    public void subscribe(MessageBus bus) {
        bus.subscribe(CHANNEL, message -> {
            try {
                JsonObject payload = JsonParser.parseString(message.payloadJson()).getAsJsonObject();
                String target = payload.get("target").getAsString();
                String command = payload.get("command").getAsString();
                String actor = payload.get("actor").getAsString();
                if (!matches(target)) {
                    return;
                }
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    plugin.getLogger().info("netexec by " + actor + ": " + command);
                    plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), command);
                });
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Ignoring malformed netexec message: " + e.getMessage());
            }
        });
    }

    boolean matches(String target) {
        if (target.equalsIgnoreCase("all")) {
            return true;
        }
        if (target.startsWith("group:")) {
            String group = target.substring("group:".length());
            return directory.byName(config.serverId()).map(NetworkServer::group).filter(group::equalsIgnoreCase)
                    .isPresent();
        }
        String server = target.startsWith("server:") ? target.substring("server:".length()) : target;
        return server.equalsIgnoreCase(config.serverId());
    }
}
