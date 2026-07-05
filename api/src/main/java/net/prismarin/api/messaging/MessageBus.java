package net.prismarin.api.messaging;

import java.util.function.Consumer;

/**
 * Publish/subscribe transport between the servers of the network.
 * Implementations wrap payloads in a {@link NetworkMessage} envelope.
 * Subscribers are invoked on the bus's internal I/O threads, never on a
 * platform main thread: handlers must not call thread-confined platform
 * API directly and should hand work off to the platform scheduler instead.
 * A throwing handler never prevents delivery to the remaining handlers.
 */
public interface MessageBus {

    /**
     * Publishes a payload on the given channel.
     * The send happens asynchronously and never blocks the calling
     * thread; delivery failures are logged by the implementation.
     *
     * @param channel the logical channel name
     * @param payloadJson the application payload as a JSON string
     */
    void publish(String channel, String payloadJson);

    /**
     * Registers a handler for all messages arriving on the given channel.
     * The subscription is active once this method returns.
     *
     * @param channel the logical channel name
     * @param handler the consumer invoked for every received message on
     *        the bus's internal threads
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
