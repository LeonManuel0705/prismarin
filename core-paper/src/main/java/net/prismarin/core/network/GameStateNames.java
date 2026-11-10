package net.prismarin.core.network;

import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.server.ServerStatus;

public final class GameStateNames {

    private GameStateNames() {
        throw new AssertionError();
    }

    public static Component render(LocaleService locale, ServerStatus status, Locale target) {
        String key = status.full() && status.state().joinable()
                ? "game-state.full"
                : "game-state." + status.state().name().toLowerCase(Locale.ROOT);
        return locale.render(key, target);
    }
}
