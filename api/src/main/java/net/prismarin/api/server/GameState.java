package net.prismarin.api.server;

public enum GameState {
    UNKNOWN,
    WAITING,
    STARTING,
    RUNNING,
    ENDING,
    RESTARTING;

    public boolean joinable() {
        return this == UNKNOWN || this == WAITING || this == STARTING;
    }
}
