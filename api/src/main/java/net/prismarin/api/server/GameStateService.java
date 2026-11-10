package net.prismarin.api.server;

public interface GameStateService {

    GameState state();

    String stateText();

    void state(GameState state);

    void state(GameState state, String text);

    void reset();
}
