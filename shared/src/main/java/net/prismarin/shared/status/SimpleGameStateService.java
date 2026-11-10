package net.prismarin.shared.status;

import net.prismarin.api.server.GameState;
import net.prismarin.api.server.GameStateService;

public final class SimpleGameStateService implements GameStateService {

    private volatile GameState manualState;
    private volatile String manualText = "";
    private volatile GameState detectedState = GameState.UNKNOWN;
    private volatile String detectedText = "";

    @Override
    public GameState state() {
        GameState manual = manualState;
        return manual != null ? manual : detectedState;
    }

    @Override
    public String stateText() {
        return manualState != null ? manualText : detectedText;
    }

    @Override
    public void state(GameState state) {
        state(state, "");
    }

    @Override
    public synchronized void state(GameState state, String text) {
        this.manualText = text == null ? "" : text;
        this.manualState = state;
    }

    @Override
    public synchronized void reset() {
        this.manualState = null;
        this.manualText = "";
    }

    public boolean manual() {
        return manualState != null;
    }

    public void detected(GameState state, String text) {
        this.detectedText = text == null ? "" : text;
        this.detectedState = state == null ? GameState.UNKNOWN : state;
    }
}
