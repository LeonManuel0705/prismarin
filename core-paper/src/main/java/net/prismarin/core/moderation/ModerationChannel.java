package net.prismarin.core.moderation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.network.ArrivalTracker;
import net.prismarin.core.social.SocialClient;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

@Singleton
public final class ModerationChannel implements PluginMessageListener {

    public static final String ID = "prismarin:moderation";

    public record Mute(long until, String reason, String id, String appeal) {

        public boolean active(long now) {
            return until <= 0L || until > now;
        }
    }

    private final PrismarinCorePlugin plugin;
    private final Provider<ReportMenu> reportMenu;
    private final ArrivalTracker arrivals;
    private final Provider<PunishMenu> punishMenu;
    private final Provider<SocialClient> social;
    private final Map<UUID, Mute> mutes = new ConcurrentHashMap<>();

    @Inject
    public ModerationChannel(PrismarinCorePlugin plugin, Provider<ReportMenu> reportMenu, ArrivalTracker arrivals,
                             Provider<PunishMenu> punishMenu, Provider<SocialClient> social) {
        this.plugin = plugin;
        this.reportMenu = reportMenu;
        this.arrivals = arrivals;
        this.punishMenu = punishMenu;
        this.social = social;
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
                case "mute" -> mutes.put(uuid, new Mute(message.get("until").getAsLong(), text(message, "reason"),
                        text(message, "id"), text(message, "appeal")));
                case "unmute" -> mutes.remove(uuid);
                case "arrival" -> {
                    if (carrier.getUniqueId().equals(uuid)) {
                        arrivals.arrived(uuid, message.has("from") ? message.get("from").getAsString() : "");
                    }
                }
                case "report-menu" -> {
                    if (carrier.getUniqueId().equals(uuid)) {
                        List<ReportMenu.Reason> reasons = new ArrayList<>();
                        for (JsonElement element : message.getAsJsonArray("reasons")) {
                            JsonObject reason = element.getAsJsonObject();
                            reasons.add(new ReportMenu.Reason(reason.get("id").getAsString(),
                                    reason.get("name").getAsString(), reason.get("icon").getAsString()));
                        }
                        reportMenu.get().open(carrier, UUID.fromString(message.get("target").getAsString()),
                                message.get("target-name").getAsString(), reasons);
                    }
                }
                case "punish-menu" -> {
                    if (carrier.getUniqueId().equals(uuid)) {
                        openPunishMenu(carrier, message);
                    }
                }
                case "social-view" -> {
                    if (carrier.getUniqueId().equals(uuid)) {
                        social.get().receive(carrier, message);
                    }
                }
                default -> {
                }
            }
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Ignoring malformed moderation message: " + e.getMessage());
        }
    }

    private void openPunishMenu(Player staff, JsonObject message) {
        List<PunishMenu.Entry> entries = new ArrayList<>();
        for (JsonElement element : message.getAsJsonArray("templates")) {
            JsonObject entry = element.getAsJsonObject();
            entries.add(new PunishMenu.Entry(entry.get("id").getAsString(), entry.get("icon").getAsString(),
                    entry.get("name").getAsString(), strings(entry, "lore")));
        }
        punishMenu.get().open(staff, UUID.fromString(message.get("target").getAsString()),
                text(message, "title"), text(message, "head"), strings(message, "head-lore"), entries);
    }

    private static String text(JsonObject message, String key) {
        JsonElement value = message.get(key);
        return value == null || value.isJsonNull() ? "" : value.getAsString();
    }

    private static List<String> strings(JsonObject message, String key) {
        List<String> values = new ArrayList<>();
        if (message.has(key) && message.get(key).isJsonArray()) {
            message.getAsJsonArray(key).forEach(element -> values.add(element.getAsString()));
        }
        return values;
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

    public void sendLocale(Player player, Locale locale) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "locale");
        message.addProperty("uuid", player.getUniqueId().toString());
        message.addProperty("locale", locale.toLanguageTag());
        player.sendPluginMessage(plugin, ID, message.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void sendReport(Player reporter, UUID target, String reason) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "report");
        message.addProperty("uuid", reporter.getUniqueId().toString());
        message.addProperty("target", target.toString());
        message.addProperty("reason", reason);
        reporter.sendPluginMessage(plugin, ID, message.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void sendPunish(Player staff, UUID target, String template) {
        JsonObject message = new JsonObject();
        message.addProperty("type", "punish");
        message.addProperty("uuid", staff.getUniqueId().toString());
        message.addProperty("target", target.toString());
        message.addProperty("template", template);
        staff.sendPluginMessage(plugin, ID, message.toString().getBytes(StandardCharsets.UTF_8));
    }

    public void sendSocial(Player player, JsonObject message) {
        message.addProperty("type", "social");
        message.addProperty("uuid", player.getUniqueId().toString());
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
