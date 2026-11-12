package net.prismarin.shared.skin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.prismarin.api.skin.ChatFace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkinFaceServiceTest {

    private static final Executor DIRECT = Runnable::run;
    private static final String TEXTURES = SkinTextures.encode("http://textures.minecraft.net/texture/"
            + SkinTexturesTest.HASH, false);
    private static final String SLIM_TEXTURES = SkinTextures.encode("http://textures.minecraft.net/texture/"
            + "b".repeat(64), true);

    @TempDir
    Path folder;

    private final List<URI> requests = new ArrayList<>();

    private SkinFaceService.Fetcher serving(String skin) {
        return uri -> {
            requests.add(uri);
            try {
                return CompletableFuture.completedFuture(FacePixelsTest.resource(skin));
            } catch (IOException e) {
                return CompletableFuture.failedFuture(e);
            }
        };
    }

    private SkinFaceService.Fetcher failing() {
        return uri -> {
            requests.add(uri);
            return CompletableFuture.failedFuture(new IOException("offline"));
        };
    }

    private SkinFaceService service(SkinFaceService.Fetcher fetcher, boolean disk, int size) {
        return new SkinFaceService(player -> CompletableFuture.completedFuture(Optional.empty()), fetcher, DIRECT,
                folder, new SkinFaceService.Settings(size, disk, 1_000L));
    }

    @Test
    void aSkinIsDownloadedOnceAndThenServedFromMemory() {
        SkinFaceService faces = service(serving("modern.png"), false, 10);
        ChatFace first = faces.fromTextures(TEXTURES, UUID.randomUUID()).join();
        ChatFace second = faces.fromTextures(TEXTURES, UUID.randomUUID()).join();
        assertFalse(first.fallback());
        assertEquals(0xFF0000, first.rgb(0, 0));
        assertSame(first, second);
        assertEquals(List.of(SkinTextures.uri(SkinTexturesTest.HASH)), requests);
        assertEquals(Optional.of(first), faces.cached(TEXTURES));
        assertEquals(1, faces.cachedFaces());
    }

    @Test
    void parallelRequestsShareOneDownload() {
        CompletableFuture<byte[]> download = new CompletableFuture<>();
        SkinFaceService faces = service(uri -> {
            requests.add(uri);
            return download;
        }, false, 10);
        CompletableFuture<ChatFace> first = faces.fromTextures(TEXTURES, UUID.randomUUID());
        CompletableFuture<ChatFace> second = faces.fromTextures(TEXTURES, UUID.randomUUID());
        assertFalse(first.isDone());
        try {
            download.complete(FacePixelsTest.resource("modern.png"));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        assertSame(first.join(), second.join());
        assertEquals(1, requests.size());
    }

    @Test
    void withoutTexturesThePlayerGetsTheDefaultFaceOfHisUuid() {
        SkinFaceService faces = service(serving("modern.png"), false, 10);
        UUID steve = new UUID(0L, 0L);
        UUID alex = new UUID(0L, 1L);
        assertEquals(DefaultFaces.STEVE_FACE, faces.face(steve).join());
        assertEquals(DefaultFaces.ALEX_FACE, faces.fromTextures("", alex).join());
        assertEquals(DefaultFaces.STEVE_FACE, faces.fromTextures(SkinTextures.encode(
                "https://evil.example/texture/" + SkinTexturesTest.HASH, false), steve).join());
        assertTrue(requests.isEmpty());
        assertTrue(DefaultFaces.STEVE_FACE.fallback());
        assertFalse(DefaultFaces.slim(steve));
        assertTrue(DefaultFaces.slim(alex));
    }

    @Test
    void aFailedDownloadFallsBackByModelAndIsTriedAgainLater() {
        SkinFaceService faces = service(failing(), false, 10);
        assertEquals(DefaultFaces.ALEX_FACE, faces.fromTextures(SLIM_TEXTURES, new UUID(0L, 0L)).join());
        assertEquals(DefaultFaces.STEVE_FACE, faces.fromTextures(TEXTURES, new UUID(0L, 1L)).join());
        assertEquals(Optional.empty(), faces.cached(TEXTURES));
        faces.fromTextures(TEXTURES, UUID.randomUUID()).join();
        assertEquals(3, requests.size());
        SkinFaceService broken = service(uri -> CompletableFuture.completedFuture(new byte[]{1, 2, 3}), false, 10);
        assertEquals(DefaultFaces.STEVE_FACE, broken.fromTextures(TEXTURES, new UUID(0L, 1L)).join());
    }

    @Test
    void facesSurviveARestartOnDisk() throws IOException {
        ChatFace stored = service(serving("modern.png"), true, 10).fromTextures(TEXTURES, UUID.randomUUID()).join();
        assertTrue(Files.isRegularFile(folder.resolve(SkinTexturesTest.HASH + ".face")));
        requests.clear();
        ChatFace loaded = service(failing(), true, 10).fromTextures(TEXTURES, UUID.randomUUID()).join();
        assertEquals(stored, loaded);
        assertTrue(requests.isEmpty());
        Files.writeString(folder.resolve(SkinTexturesTest.HASH + ".face"), "broken");
        assertTrue(service(failing(), true, 10).fromTextures(TEXTURES, UUID.randomUUID()).join().fallback());
    }

    @Test
    void theMemoryCacheKeepsOnlyTheNewestFaces() {
        SkinFaceService faces = service(serving("modern.png"), false, 1);
        faces.fromTextures(TEXTURES, UUID.randomUUID()).join();
        faces.fromTextures(SLIM_TEXTURES, UUID.randomUUID()).join();
        assertEquals(1, faces.cachedFaces());
        assertEquals(Optional.empty(), faces.cached(TEXTURES));
        faces.fromTextures(TEXTURES, UUID.randomUUID()).join();
        assertEquals(3, requests.size());
    }

    @Test
    void aPlayersOwnTexturesAreLookedUp() {
        UUID player = UUID.randomUUID();
        SkinFaceService faces = new SkinFaceService(uuid -> CompletableFuture.completedFuture(
                uuid.equals(player) ? Optional.of(TEXTURES) : Optional.empty()), serving("modern.png"), DIRECT, folder,
                SkinFaceService.Settings.DEFAULT);
        assertEquals(0xFF0000, faces.face(player).join().rgb(0, 0));
        assertEquals(8, faces.lines(player).join().size());
        SkinFaceService failingLookup = new SkinFaceService(uuid -> CompletableFuture.failedFuture(
                new IllegalStateException("gone")), serving("modern.png"), DIRECT, folder,
                SkinFaceService.Settings.DEFAULT);
        assertEquals(DefaultFaces.STEVE_FACE, failingLookup.face(new UUID(0L, 0L)).join());
    }
}
