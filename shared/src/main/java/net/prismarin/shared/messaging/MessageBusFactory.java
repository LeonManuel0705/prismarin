package net.prismarin.shared.messaging;

import net.prismarin.api.messaging.MessageBus;
import net.prismarin.shared.config.CoreConfig;

public final class MessageBusFactory {

    private MessageBusFactory() {
        throw new AssertionError();
    }

    public static MessageBus create(CoreConfig config) {
        if (config.redisEnabled()) {
            return new RedisMessageBus(config.serverId(), config.redisHost(), config.redisPort(), config.redisPassword());
        }
        return new LocalMessageBus(config.serverId());
    }
}
