package net.prismarin.velocity.queue;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.queue.QueuePosition;
import net.prismarin.api.queue.QueueTarget;
import net.prismarin.velocity.locale.ProxyMessages;

final class QueueDisplay {

    private final ProxyServer proxy;
    private final ProxyMessages messages;
    private final Function<QueueTarget, Component> names;
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();

    QueueDisplay(ProxyServer proxy, ProxyMessages messages, Function<QueueTarget, Component> names) {
        this.proxy = proxy;
        this.messages = messages;
        this.names = names;
    }

    void show(Map<UUID, QueuePosition> shown, QueueSettings.Display display) {
        for (Map.Entry<UUID, QueuePosition> entry : shown.entrySet()) {
            Optional<Player> online = proxy.getPlayer(entry.getKey());
            if (online.isEmpty()) {
                continue;
            }
            Player player = online.get();
            QueuePosition position = entry.getValue();
            TagResolver[] resolvers = resolvers(position);
            String suffix = position.paused() ? "-paused" : "";
            if (display.actionBar()) {
                player.sendActionBar(messages.render(player, "queue.action-bar" + suffix, resolvers));
            }
            if (display.bossBar()) {
                Component title = messages.render(player, "queue.boss-bar" + suffix, resolvers);
                float progress = progress(position.position(), position.size());
                BossBar bar = bars.get(player.getUniqueId());
                if (bar == null) {
                    bar = BossBar.bossBar(title, progress, display.color(), display.overlay());
                    bars.put(player.getUniqueId(), bar);
                    player.showBossBar(bar);
                } else {
                    bar.name(title);
                    bar.progress(progress);
                    bar.color(display.color());
                    bar.overlay(display.overlay());
                }
            }
        }
        for (UUID player : bars.keySet()) {
            if (!display.bossBar() || !shown.containsKey(player)) {
                hide(player, false);
            }
        }
    }

    void hide(UUID player, boolean clearActionBar) {
        BossBar bar = bars.remove(player);
        Optional<Player> online = proxy.getPlayer(player);
        if (online.isEmpty()) {
            return;
        }
        if (bar != null) {
            online.get().hideBossBar(bar);
        }
        if (clearActionBar) {
            online.get().sendActionBar(Component.empty());
        }
    }

    void forget(UUID player) {
        bars.remove(player);
    }

    void clear() {
        for (UUID player : bars.keySet()) {
            hide(player, false);
        }
    }

    TagResolver[] resolvers(QueuePosition position) {
        return new TagResolver[]{
                Placeholder.component("target", names.apply(position.target())),
                Placeholder.unparsed("position", String.valueOf(position.position())),
                Placeholder.unparsed("size", String.valueOf(position.size())),
                Placeholder.unparsed("players", String.valueOf(position.players())),
                Placeholder.unparsed("party", String.valueOf(position.unitSize()))
        };
    }

    static float progress(int position, int size) {
        if (size <= 1 || position <= 1) {
            return 1F;
        }
        float value = (size - position) / (float) (size - 1);
        return Math.max(0F, Math.min(1F, value));
    }
}
