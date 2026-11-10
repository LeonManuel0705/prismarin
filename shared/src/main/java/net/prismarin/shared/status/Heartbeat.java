package net.prismarin.shared.status;

public record Heartbeat(String serverId, int players, int maxPlayers, double tps, double mspt, long usedMemoryMb,
                        long maxMemoryMb, long uptimeSeconds, String version, long sentAt, String state,
                        String stateText) {
}
