package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.ServerPing;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

public final class VanishRegistry {

    public static final String SEE_PERMISSION = "prismarin.vanish.see";

    private final Map<UUID, Integer> vanished = new ConcurrentHashMap<>();
    private final List<BiConsumer<UUID, Boolean>> listeners = new CopyOnWriteArrayList<>();

    public void update(UUID player, boolean state, int level) {
        boolean changed;
        if (state) {
            changed = vanished.put(player, Math.max(1, level)) == null;
        } else {
            changed = vanished.remove(player) != null;
        }
        if (changed) {
            listeners.forEach(listener -> listener.accept(player, state));
        }
    }

    public void onChange(BiConsumer<UUID, Boolean> listener) {
        listeners.add(listener);
    }

    public boolean vanished(UUID player) {
        return vanished.containsKey(player);
    }

    public boolean canSee(CommandSource viewer, Player target) {
        return !vanished(target.getUniqueId()) || viewer.hasPermission(SEE_PERMISSION)
                || viewer instanceof Player player && player.getUniqueId().equals(target.getUniqueId());
    }

    public int count() {
        return vanished.size();
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        vanished.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onPing(ProxyPingEvent event) {
        if (vanished.isEmpty()) {
            return;
        }
        ServerPing ping = event.getPing();
        ServerPing.Builder builder = ping.asBuilder();
        builder.onlinePlayers(Math.max(0, ping.getPlayers().map(ServerPing.Players::getOnline).orElse(0) - vanished.size()));
        ping.getPlayers().ifPresent(players -> {
            builder.clearSamplePlayers();
            players.getSample().stream()
                    .filter(sample -> !vanished.containsKey(sample.getId()))
                    .forEach(builder::samplePlayers);
        });
        event.setPing(builder.build());
    }
}
