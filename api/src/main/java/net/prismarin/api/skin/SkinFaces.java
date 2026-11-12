package net.prismarin.api.skin;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;

public interface SkinFaces {

    CompletableFuture<ChatFace> face(UUID player);

    CompletableFuture<ChatFace> fromTextures(String textures, UUID owner);

    ChatFace defaultFace(UUID owner);

    Optional<ChatFace> cached(String textures);

    default CompletableFuture<List<Component>> lines(UUID player) {
        return face(player).thenApply(ChatFace::lines);
    }
}
