package net.prismarin.api.messaging;

/**
 * Envelope for a message travelling across the network message bus.
 *
 * @param id a globally unique message id
 * @param channel the logical channel the message was published on
 * @param sourceServer the id of the server that published the message
 * @param timestamp the publish time in epoch milliseconds
 * @param payloadJson the application payload as a JSON string
 */
public record NetworkMessage(String id, String channel, String sourceServer, long timestamp, String payloadJson) {
}
