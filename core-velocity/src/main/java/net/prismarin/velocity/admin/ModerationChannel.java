package net.prismarin.velocity.admin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import net.prismarin.velocity.locale.PlayerLocaleRegistry;

public final class ModerationChannel {

    public static final MinecraftChannelIdentifier ID = MinecraftChannelIdentifier.from("prismarin:moderation");

    public interface ReportHandler {

        void submit(Player reporter, UUID target, String reason);
    }

    public interface PunishHandler {

        void choose(Player staff, UUID target, String template);
    }

    public record PunishMenuEntry(String id, String icon, String name, List<String> lore) {
    }

    public interface SocialHandler {

        void handle(Player player, JsonObject message);
    }

    private final VanishRegistry vanish;
    private final PlayerLocaleRegistry locales;
    private volatile OperatorRegistry operators;
    private volatile ReportHandler reports;
    private volatile PunishHandler punishments;
    private volatile SocialHandler social;

    public ModerationChannel(VanishRegistry vanish, PlayerLocaleRegistry locales) {
        this.vanish = vanish;
        this.locales = locales;
    }

    public void handleReports(ReportHandler reports) {
        this.reports = reports;
    }

    public void handlePunishments(PunishHandler punishments) {
        this.punishments = punishments;
    }

    public void sendPunishMenu(Player staff, UUID target, String targetName, String title, String head,
                               List<String> headLore, List<PunishMenuEntry> templates) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "punish-menu");
        message.addProperty("uuid", staff.getUniqueId().toString());
        message.addProperty("target", target.toString());
        message.addProperty("target-name", targetName);
        message.addProperty("title", title);
        message.addProperty("head", head);
        message.add("head-lore", strings(headLore));
        JsonArray entries = new JsonArray();
        for (PunishMenuEntry template : templates) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", template.id());
            entry.addProperty("icon", template.icon());
            entry.addProperty("name", template.name());
            entry.add("lore", strings(template.lore()));
            entries.add(entry);
        }
        message.add("templates", entries);
        send(staff, message);
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    public void handleSocial(SocialHandler social) {
        this.social = social;
    }

    public void sendSocial(Player player, JsonObject message) {
        message.addProperty("uuid", player.getUniqueId().toString());
        send(player, message);
    }

    public void sendReportMenu(Player reporter, UUID target, String shownName, List<ReportMenuEntry> reasons) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "report-menu");
        message.addProperty("uuid", reporter.getUniqueId().toString());
        message.addProperty("target", target.toString());
        message.addProperty("target-name", shownName);
        JsonArray entries = new JsonArray();
        for (ReportMenuEntry reason : reasons) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", reason.id());
            entry.addProperty("name", reason.name());
            entry.addProperty("icon", reason.icon());
            entries.add(entry);
        }
        message.add("reasons", entries);
        send(reporter, message);
    }

    public record ReportMenuEntry(String id, String name, String icon) {
    }

    public void trustOperators(OperatorRegistry operators) {
        this.operators = operators;
    }

    public void sendMute(Player player, long until, String reason) {
        sendMute(player, until, reason, "", "");
    }

    public void sendMute(Player player, long until, String reason, String id, String appeal) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "mute");
        message.addProperty("uuid", player.getUniqueId().toString());
        message.addProperty("until", until);
        message.addProperty("reason", reason);
        message.addProperty("id", id);
        message.addProperty("appeal", appeal);
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
    public void onServerPostConnect(ServerPostConnectEvent event) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "arrival");
        message.addProperty("uuid", event.getPlayer().getUniqueId().toString());
        message.addProperty("from", event.getPreviousServer() == null ? ""
                : event.getPreviousServer().getServerInfo().getName());
        send(event.getPlayer(), message);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!event.getIdentifier().equals(ID)) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        if (!(event.getSource() instanceof ServerConnection connection)) {
            return;
        }
        try {
            JsonObject message = JsonParser.parseString(new String(event.getData(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            String type = message.get("type").getAsString();
            OperatorRegistry registry = operators;
            if ("operator".equals(type) && registry != null) {
                registry.update(UUID.fromString(message.get("uuid").getAsString()), message.get("op").getAsBoolean());
            }
            UUID subject = UUID.fromString(message.get("uuid").getAsString());
            Player carrier = connection.getPlayer();
            if ("locale".equals(type) && carrier.getUniqueId().equals(subject)) {
                locales.update(subject, message.get("locale").getAsString(), connection.getServerInfo().getName());
            }
            ReportHandler handler = reports;
            if ("report".equals(type) && handler != null && carrier.getUniqueId().equals(subject)) {
                handler.submit(carrier, UUID.fromString(message.get("target").getAsString()),
                        message.get("reason").getAsString());
            }
            PunishHandler punish = punishments;
            if ("punish".equals(type) && punish != null && carrier.getUniqueId().equals(subject)) {
                punish.choose(carrier, UUID.fromString(message.get("target").getAsString()),
                        message.get("template").getAsString());
            }
            SocialHandler socialHandler = social;
            if ("social".equals(type) && socialHandler != null && carrier.getUniqueId().equals(subject)) {
                socialHandler.handle(carrier, message);
            }
            if ("vanish".equals(type)) {
                vanish.update(UUID.fromString(message.get("uuid").getAsString()),
                        message.get("vanished").getAsBoolean(), message.has("level")
                                ? message.get("level").getAsInt() : 1);
            }
        } catch (RuntimeException ignored) {
            return;
        }
    }
}
