package net.prismarin.shared.messaging;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.messaging.NetworkMessage;

public final class LocalMessageBus implements MessageBus {

    private static final Logger LOGGER = Logger.getLogger(LocalMessageBus.class.getName());

    private final String serverId;
    private final Map<String, CopyOnWriteArrayList<Consumer<NetworkMessage>>> subscribers = new ConcurrentHashMap<>();

    public LocalMessageBus(String serverId) {
        this.serverId = serverId;
    }

    @Override
    public void publish(String channel, String payloadJson) {
        NetworkMessage message = new NetworkMessage(
                UUID.randomUUID().toString(),
                channel,
                serverId,
                System.currentTimeMillis(),
                payloadJson
        );
        List<Consumer<NetworkMessage>> handlers = subscribers.get(channel);
        if (handlers == null) {
            return;
        }
        for (Consumer<NetworkMessage> handler : handlers) {
            try {
                handler.accept(message);
            } catch (Throwable throwable) {
                LOGGER.log(Level.WARNING, "Message handler on channel " + channel + " failed", throwable);
            }
        }
    }

    @Override
    public void subscribe(String channel, Consumer<NetworkMessage> handler) {
        subscribers.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(handler);
    }

    @Override
    public void close() {
        subscribers.clear();
    }

    @Override
    public String serverId() {
        return serverId;
    }
}
