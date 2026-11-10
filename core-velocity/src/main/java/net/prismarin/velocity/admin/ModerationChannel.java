package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class ModerationChannel {

    public static final MinecraftChannelIdentifier ID = MinecraftChannelIdentifier.from("prismarin:moderation");

    private final VanishRegistry vanish;

    public ModerationChannel(VanishRegistry vanish) {
        this.vanish = vanish;
    }

    public void sendMute(Player player, long until, String reason) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "mute");
        message.addProperty("uuid", player.getUniqueId().toString());
        message.addProperty("until", until);
        message.addProperty("reason", reason);
        send(player, message);
    }

    public void sendUnmute(Player player) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "unmute");
        message.addProperty("uuid", player.getUniqueId().toString());
        send(player, message);
    }

    private void send(Player player, JsonObject message) {
        player.getCurrentServer().ifPresent(connection ->
                connection.sendPluginMessage(ID, message.toString().getBytes(StandardCharsets.UTF_8)));
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(ID)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection)) {
            return;
        }
        try {
            JsonObject message = JsonParser.parseString(new String(event.getData(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if ("vanish".equals(message.get("type").getAsString())) {
                vanish.update(UUID.fromString(message.get("uuid").getAsString()),
                        message.get("vanished").getAsBoolean(), message.has("level")
                                ? message.get("level").getAsInt() : 1);
            }
        } catch (RuntimeException ignored) {
            return;
        }
    }
}
