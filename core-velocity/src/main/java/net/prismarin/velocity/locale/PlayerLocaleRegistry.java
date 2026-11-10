package net.prismarin.velocity.locale;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerLocaleRegistry {

    private record Entry(Locale locale, String server) {
    }

    private final Map<UUID, Entry> entries = new ConcurrentHashMap<>();

    public void update(UUID player, String languageTag, String server) {
        if (languageTag == null || languageTag.isBlank()) {
            return;
        }
        entries.put(player, new Entry(Locale.forLanguageTag(languageTag.replace('_', '-')), server));
    }

    public Optional<Locale> locale(UUID player) {
        if (player == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(player)).map(Entry::locale);
    }

    public boolean prismarinBackend(Player player) {
        Entry entry = entries.get(player.getUniqueId());
        return entry != null && player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName().equalsIgnoreCase(entry.server()))
                .orElse(false);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        entries.remove(event.getPlayer().getUniqueId());
    }
}
