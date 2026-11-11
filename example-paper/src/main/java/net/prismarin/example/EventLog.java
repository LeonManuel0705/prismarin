package net.prismarin.example;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import net.prismarin.api.economy.BalanceChangedEvent;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.event.Subscription;
import net.prismarin.api.moderation.ReportFiledEvent;
import net.prismarin.api.nick.NickChangedEvent;
import net.prismarin.api.player.ServerChangedEvent;
import net.prismarin.api.profile.ProfileLoadedEvent;
import net.prismarin.api.profile.ProfileUnloadedEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

final class EventLog {

    private final Plugin plugin;
    private final Logger logger;
    private final StatsStore stats;
    private final Texts texts;
    private final List<Subscription> subscriptions = new ArrayList<>();

    EventLog(Plugin plugin, StatsStore stats, Texts texts) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.stats = stats;
        this.texts = texts;
    }

    void subscribe(EventBus events) {
        subscriptions.add(events.subscribe(ProfileLoadedEvent.class, event ->
                stats.load(event.profile().getUuid(), event.profile().getName())));
        subscriptions.add(events.subscribe(ProfileUnloadedEvent.class, event ->
                stats.forget(event.profile().getUuid())));
        subscriptions.add(events.subscribe(BalanceChangedEvent.class, event ->
                logger.info(event.uuid() + " " + event.type() + " " + event.amount() + " " + event.currency().id()
                        + ", balance " + event.newBalance())));
        subscriptions.add(events.subscribe(NickChangedEvent.class, event ->
                logger.info(event.realName() + " is now shown as " + event.currentName())));
        subscriptions.add(events.subscribe(ReportFiledEvent.class, event ->
                logger.info("Report " + event.report().id() + " against " + event.report().targetName() + ": "
                        + event.report().reason())));
        subscriptions.add(events.subscribe(ServerChangedEvent.class, event -> {
            if (event.joinedNetwork()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    Player player = plugin.getServer().getPlayer(event.player());
                    if (player != null) {
                        texts.send(player, "welcome", "name", player.getName());
                    }
                });
            }
        }));
    }

    void unsubscribe() {
        subscriptions.forEach(Subscription::unsubscribe);
        subscriptions.clear();
    }
}
