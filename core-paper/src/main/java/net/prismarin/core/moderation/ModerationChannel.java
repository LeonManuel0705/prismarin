package net.prismarin.core.moderation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

@Singleton
public final class ModerationChannel implements PluginMessageListener {

    public static final String ID = "prismarin:moderation";

    public record Mute(long until, String reason) {

        public boolean active(long now) {
            return until <= 0L || until > now;
        }
    }

    private final PrismarinCorePlugin plugin;
    private final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();

    @Inject
    public ModerationChannel(PrismarinCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player carrier, byte[] data) {
        if (!ID.equals(channel)) {
            return;
        }
        try {
            JsonObject message = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            UUID uuid = UUID.fromString(message.get("uuid").getAsString());
            switch (message.get("type").getAsString()) {
                case "mute" -> mutes.put(uuid, new Mute(message.get("until").getAsLong(),
                        message.has("reason") ? message.get("reason").getAsString() : ""));
                case "unmute" -> mutes.remove(uuid);
                default -> {
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Ignoring malformed moderation message: " + e.getMessage());
        }
    }

    public Mute mute(UUID player) {
        Mute mute = mutes.get(player);
        if (mute != null && !mute.active(System.currentTimeMillis())) {
            mutes.remove(player);
            return null;
        }
        return mute;
    }

    public void forget(UUID player) {
        mutes.remove(player);
    }

    public void sendOperator(Player player) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "operator");
        message.addProperty("uuid", player.getUniqueId().toString());
        message.addProperty("op", player.isOp());
        player.sendPluginMessage(plugin, ID, message.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void sendVanish(Player player, boolean vanished, int level) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "vanish");
        message.addProperty("uuid", player.getUniqueId().toString());
        message.addProperty("vanished", vanished);
        message.addProperty("level", level);
        player.sendPluginMessage(plugin, ID, message.toString().getBytes(StandardCharsets.UTF_8));
    }
}
