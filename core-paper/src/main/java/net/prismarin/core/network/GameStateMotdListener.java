package net.prismarin.core.network;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.kyori.adventure.text.Component;
import net.prismarin.api.server.GameState;
import net.prismarin.core.config.GameStateSettings;
import net.prismarin.shared.status.GameStateParser;
import net.prismarin.shared.status.SimpleGameStateService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

@Singleton
public final class GameStateMotdListener implements Listener {

    private final SimpleGameStateService gameState;
    private final GameStateSettings settings;

    @Inject
    public GameStateMotdListener(SimpleGameStateService gameState, GameStateSettings settings) {
        this.gameState = gameState;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPing(PaperServerListPingEvent event) {
        GameState state = gameState.state();
        if (settings.writeMotd() && state != GameState.UNKNOWN) {
            event.motd(Component.text(GameStateParser.motd(state, gameState.stateText())));
        }
    }
}
