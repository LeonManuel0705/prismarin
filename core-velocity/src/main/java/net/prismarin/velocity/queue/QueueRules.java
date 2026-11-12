package net.prismarin.velocity.queue;

public record QueueRules(boolean enabled, int maxPlayers, boolean respectGameState, boolean queueWhenOffline,
                         boolean holdForParties) {

    public static final QueueRules DEFAULT = new QueueRules(true, 0, true, false, true);

    public QueueRules {
        maxPlayers = Math.max(0, maxPlayers);
    }

    public int capacity(int serverMax) {
        if (maxPlayers <= 0) {
            return Math.max(0, serverMax);
        }
        return serverMax > 0 ? Math.min(maxPlayers, serverMax) : maxPlayers;
    }

    public record Override(Boolean enabled, Integer maxPlayers, Boolean respectGameState, Boolean queueWhenOffline,
                           Boolean holdForParties) {

        public static final Override NONE = new Override(null, null, null, null, null);

        public QueueRules apply(QueueRules base) {
            return new QueueRules(enabled == null ? base.enabled() : enabled,
                    maxPlayers == null ? base.maxPlayers() : maxPlayers,
                    respectGameState == null ? base.respectGameState() : respectGameState,
                    queueWhenOffline == null ? base.queueWhenOffline() : queueWhenOffline,
                    holdForParties == null ? base.holdForParties() : holdForParties);
        }
    }
}
