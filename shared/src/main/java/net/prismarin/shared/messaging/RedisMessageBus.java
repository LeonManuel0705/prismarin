package net.prismarin.shared.messaging;

import com.google.gson.Gson;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.messaging.NetworkMessage;

public final class RedisMessageBus implements MessageBus {

    private final String serverId;
    private final Gson gson = new Gson();
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> pubConnection;
    private final StatefulRedisPubSubConnection<String, String> subConnection;
    private final Map<String, CopyOnWriteArrayList<Consumer<NetworkMessage>>> subscribers = new ConcurrentHashMap<>();

    public RedisMessageBus(String serverId, String host, int port, String password) {
        this.serverId = serverId;
        RedisURI.Builder uri = RedisURI.builder().withHost(host).withPort(port);
        if (password != null && !password.isEmpty()) {
            uri.withPassword(password.toCharArray());
        }
        this.client = RedisClient.create(uri.build());
        this.pubConnection = client.connect();
        this.subConnection = client.connectPubSub();
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
            handler.accept(message);
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
        pubConnection.sync().publish(channel, gson.toJson(message));
    }

    @Override
    public void subscribe(String channel, Consumer<NetworkMessage> handler) {
        subscribers.computeIfAbsent(channel, key -> {
            subConnection.sync().subscribe(key);
            return new CopyOnWriteArrayList<>();
        }).add(handler);
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
