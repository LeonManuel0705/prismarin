package net.prismarin.shared.messaging;

import com.google.gson.Gson;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.messaging.NetworkMessage;

public final class RedisMessageBus implements MessageBus {

    private static final Logger LOGGER = Logger.getLogger(RedisMessageBus.class.getName());

    private final String serverId;
    private final Gson gson = new Gson();
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> pubConnection;
    private final StatefulRedisPubSubConnection<String, String> subConnection;
    private final Map<String, CopyOnWriteArrayList<Consumer<NetworkMessage>>> subscribers = new ConcurrentHashMap<>();
    private final Set<String> subscribedChannels = ConcurrentHashMap.newKeySet();

    public RedisMessageBus(String serverId, String host, int port, String password) {
        this.serverId = serverId;
        RedisURI.Builder uri = RedisURI.builder().withHost(host).withPort(port);
        if (password != null && !password.isEmpty()) {
            uri.withPassword(password.toCharArray());
        }
        RedisClient createdClient = RedisClient.create(uri.build());
        StatefulRedisConnection<String, String> createdPub = null;
        StatefulRedisPubSubConnection<String, String> createdSub = null;
        try {
            createdPub = createdClient.connect();
            createdSub = createdClient.connectPubSub();
        } catch (RuntimeException e) {
            if (createdPub != null) {
                createdPub.close();
            }
            createdClient.shutdown();
            throw e;
        }
        this.client = createdClient;
        this.pubConnection = createdPub;
        this.subConnection = createdSub;
        this.subConnection.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                dispatch(channel, message);
            }
        });
    }

    private void dispatch(String channel, String json) {
        List<Consumer<NetworkMessage>> handlers = subscribers.get(channel);
        if (handlers == null) {
            return;
        }
        NetworkMessage message = gson.fromJson(json, NetworkMessage.class);
        for (Consumer<NetworkMessage> handler : handlers) {
            try {
                handler.accept(message);
            } catch (Throwable throwable) {
                LOGGER.log(Level.WARNING, "Message handler on channel " + channel + " failed", throwable);
            }
        }
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
        pubConnection.async().publish(channel, gson.toJson(message)).whenComplete((receivers, throwable) -> {
            if (throwable != null) {
                LOGGER.log(Level.WARNING, "Could not publish message on channel " + channel, throwable);
            }
        });
    }

    @Override
    public void subscribe(String channel, Consumer<NetworkMessage> handler) {
        subscribers.computeIfAbsent(channel, key -> new CopyOnWriteArrayList<>()).add(handler);
        if (subscribedChannels.add(channel)) {
            try {
                subConnection.sync().subscribe(channel);
            } catch (RuntimeException e) {
                subscribedChannels.remove(channel);
                throw e;
            }
        }
    }

    @Override
    public void close() {
        subConnection.close();
        pubConnection.close();
        client.shutdown();
    }

    @Override
    public String serverId() {
        return serverId;
    }
}
