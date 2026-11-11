package net.prismarin.example;

import java.util.Locale;
import java.util.Optional;
import net.prismarin.api.server.GameState;
import net.prismarin.api.server.GameStateService;

final class RoundState {

    private final GameStateService states;

    RoundState(GameStateService states) {
        this.states = states;
    }

    void open() {
        states.state(GameState.WAITING, "0/8");
    }

    Optional<GameState> set(String name) {
        try {
            GameState state = GameState.valueOf(name.toUpperCase(Locale.ROOT));
            states.state(state);
            return Optional.of(state);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    void close() {
        states.reset();
    }
}
