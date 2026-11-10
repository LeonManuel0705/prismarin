package net.prismarin.velocity.admin;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.server.ServerStatus;

public final class AlertMonitor implements Runnable {

    public static final String PERMISSION = "prismarin.staff.alerts";

    private final AdminContext context;
    private final Map<String, Boolean> online = new HashMap<>();
    private final Map<String, Boolean> lagging = new HashMap<>();

    public AlertMonitor(AdminContext context) {
        this.context = context;
    }

    @Override
    public void run() {
        context.status().refresh().thenAccept(statuses -> {
            synchronized (this) {
                for (ServerStatus status : statuses) {
                    Boolean before = online.put(status.name(), status.online());
                    if (before != null && before != status.online()) {
                        String key = status.online() ? "alerts.server-up" : "alerts.server-down";
                        context.notifyStaff(PERMISSION, key, Placeholder.unparsed("server", status.name()));
                        DiscordWebhook.post(context.settings().discordWebhook(), (status.online() ? "Online: "
                                : "Offline: ") + status.name());
                    }
                    boolean lag = status.online() && status.prismarin() && status.tps() < context.settings().lowTps();
                    Boolean lagBefore = lagging.put(status.name(), lag);
                    if (lag && !Boolean.TRUE.equals(lagBefore)) {
                        String tps = String.format(Locale.ROOT, "%.1f", status.tps());
                        context.notifyStaff(PERMISSION, "alerts.low-tps", Placeholder.unparsed("server", status.name()),
                                Placeholder.unparsed("tps", tps));
                        DiscordWebhook.post(context.settings().discordWebhook(), "Low TPS on " + status.name() + ": "
                                + tps);
                    }
                }
            }
        });
    }
}
