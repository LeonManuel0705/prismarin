package net.prismarin.api.messaging;

public record NetworkMessage(String id, String channel, String sourceServer, long timestamp, String payloadJson) {
}
