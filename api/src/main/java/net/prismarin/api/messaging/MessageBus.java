package net.prismarin.api.messaging;

import java.util.function.Consumer;

public interface MessageBus {

    void publish(String channel, String payloadJson);

    void subscribe(String channel, Consumer<NetworkMessage> handler);

    void close();

    String serverId();
}
