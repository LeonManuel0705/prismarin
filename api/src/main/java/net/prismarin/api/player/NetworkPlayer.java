package net.prismarin.api.player;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record NetworkPlayer(UUID uuid, String realName, String nick, Locale locale, String server, String world,
                            boolean online, boolean vanished, RankDisplay rank, RankDisplay realRank, int level,
                            long lastSeen, Source source) {

    public enum Source {
        LOCAL,
        PROXY,
        STORAGE
    }

    public NetworkPlayer {
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(source, "source");
        realName = realName == null ? "" : realName;
        locale = locale == null ? Locale.ROOT : locale;
        nick = online && nick != null ? nick.trim() : "";
        server = online && server != null ? server : "";
        world = online && world != null ? world : "";
        vanished = online && vanished;
        rank = rank == null ? RankDisplay.NONE : rank;
        realRank = realRank == null ? rank : realRank;
        level = Math.max(0, level);
    }

    public boolean nicked() {
        return !nick.isEmpty();
    }

    public String shownName() {
        return nicked() ? nick : realName;
    }

    public Optional<String> nickName() {
        return nicked() ? Optional.of(nick) : Optional.empty();
    }

    public Optional<String> currentServer() {
        return server.isEmpty() ? Optional.empty() : Optional.of(server);
    }

    public Optional<String> currentWorld() {
        return world.isEmpty() ? Optional.empty() : Optional.of(world);
    }

    public boolean onServer(String name) {
        return online && name != null && server.equalsIgnoreCase(name);
    }

    public boolean live() {
        return source != Source.STORAGE;
    }

    public NetworkPlayer asOffline() {
        return new NetworkPlayer(uuid, realName, "", locale, "", "", false, false, realRank, realRank, level,
                lastSeen, source);
    }
}
