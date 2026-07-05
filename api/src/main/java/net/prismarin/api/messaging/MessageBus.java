package net.prismarin.api.messaging;

import java.util.function.Consumer;

/**
 * Publish/subscribe transport between the servers of the network.
 * Implementations wrap payloads in a {@link NetworkMessage} envelope and
 * may dispatch to subscribers on arbitrary threads.
 */
public interface MessageBus {

    /**
     * Publishes a payload on the given channel.
     *
     * @param channel the logical channel name
     * @param payloadJson the application payload as a JSON string
     */
    void publish(String channel, String payloadJson);

    /**
     * Registers a handler for all messages arriving on the given channel.
     *
     * @param channel the logical channel name
     * @param handler the consumer invoked for every received message
     */
    void subscribe(String channel, Consumer<NetworkMessage> handler);

    /**
     * Releases all underlying connections and subscriptions.
     */
    void close();

    /**
     * Returns the id of the server this bus instance belongs to.
     *
     * @return the local server id used as message source
     */
    String serverId();
}
