package net.prismarin.shared.skin;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.prismarin.api.skin.ChatFace;
import net.prismarin.api.skin.SkinFaces;

public final class SkinFaceService implements SkinFaces {

    public static final String FOLDER = "skin-faces";
    public static final int MAX_BYTES = 256 * 1024;
    private static final Logger LOGGER = Logger.getLogger(SkinFaceService.class.getName());

    @FunctionalInterface
    public interface Fetcher {
        CompletableFuture<byte[]> fetch(URI uri);
    }

    @FunctionalInterface
    public interface Textures {
        CompletableFuture<Optional<String>> of(UUID player);
    }

    public record Settings(int cacheSize, boolean diskCache, long timeoutMillis) {

        public static final Settings DEFAULT = new Settings(512, true, 3_000L);

        public Settings {
            cacheSize = Math.clamp(cacheSize, 1, 100_000);
            timeoutMillis = Math.clamp(timeoutMillis, 250L, 30_000L);
        }
    }

    private final Textures textures;
    private final Fetcher fetcher;
    private final Executor executor;
    private final Path folder;
    private final Map<String, ChatFace> memory;
    private final Map<String, CompletableFuture<ChatFace>> loading = new ConcurrentHashMap<>();

    public SkinFaceService(Textures textures, Fetcher fetcher, Executor executor, Path folder, Settings settings) {
        this.textures = textures;
        this.fetcher = fetcher;
        this.executor = executor;
        this.folder = settings.diskCache() ? folder : null;
        int limit = settings.cacheSize();
        this.memory = new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, ChatFace> eldest) {
                return size() > limit;
            }
        };
    }

    public static SkinFaceService create(Textures textures, Executor executor, Path folder, Settings settings) {
        return new SkinFaceService(textures, http(settings.timeoutMillis()), executor, folder, settings);
    }

    public static Fetcher http(long timeoutMillis) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return uri -> {
            if (!SkinTextures.allowed(uri)) {
                return CompletableFuture.failedFuture(new IllegalArgumentException("not a skin texture: " + uri));
            }
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMillis)).GET().build();
            return client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray()).thenApply(response -> {
                if (response.statusCode() != 200) {
                    throw new IllegalStateException("the texture server answered " + response.statusCode());
                }
                if (response.body().length > MAX_BYTES) {
                    throw new IllegalStateException("the skin is larger than " + MAX_BYTES + " bytes");
                }
                return response.body();
            });
        };
    }

    @Override
    public CompletableFuture<ChatFace> face(UUID player) {
        return textures.of(player).exceptionally(error -> Optional.empty())
                .thenCompose(value -> value.map(found -> fromTextures(found, player))
                        .orElseGet(() -> CompletableFuture.completedFuture(defaultFace(player))));
    }

    @Override
    public CompletableFuture<ChatFace> fromTextures(String value, UUID owner) {
        Optional<SkinTextures.Skin> skin = SkinTextures.parse(value);
        if (skin.isEmpty()) {
            return CompletableFuture.completedFuture(defaultFace(owner));
        }
        String hash = skin.get().hash();
        Optional<ChatFace> known = remembered(hash);
        if (known.isPresent()) {
            return CompletableFuture.completedFuture(known.get());
        }
        CompletableFuture<ChatFace> pending = loading.computeIfAbsent(hash, key -> load(skin.get()));
        pending.whenComplete((face, error) -> loading.remove(hash, pending));
        return pending;
    }

    @Override
    public ChatFace defaultFace(UUID owner) {
        return DefaultFaces.forOwner(owner);
    }

    @Override
    public Optional<ChatFace> cached(String value) {
        return SkinTextures.parse(value).flatMap(skin -> remembered(skin.hash()));
    }

    public int cachedFaces() {
        synchronized (memory) {
            return memory.size();
        }
    }

    private Optional<ChatFace> remembered(String hash) {
        synchronized (memory) {
            return Optional.ofNullable(memory.get(hash));
        }
    }

    private void remember(ChatFace face) {
        synchronized (memory) {
            memory.put(face.source(), face);
        }
    }

    private CompletableFuture<ChatFace> load(SkinTextures.Skin skin) {
        return CompletableFuture.supplyAsync(() -> fromDisk(skin.hash()), executor).thenCompose(stored -> {
            if (stored.isPresent()) {
                remember(stored.get());
                return CompletableFuture.completedFuture(stored.get());
            }
            CompletableFuture<byte[]> download;
            try {
                download = fetcher.fetch(skin.uri());
            } catch (RuntimeException e) {
                download = CompletableFuture.failedFuture(e);
            }
            return download.thenApplyAsync(bytes -> {
                ChatFace face = new ChatFace(FacePixels.read(bytes), skin.hash(), false);
                remember(face);
                toDisk(face);
                return face;
            }, executor);
        }).exceptionally(error -> {
            LOGGER.log(Level.FINE, "Could not load the skin " + skin.hash(), error);
            return DefaultFaces.forModel(skin.slim());
        });
    }

    private Optional<ChatFace> fromDisk(String hash) {
        if (folder == null) {
            return Optional.empty();
        }
        Path file = folder.resolve(hash + ".face");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            String[] parts = Files.readString(file, StandardCharsets.US_ASCII).strip().split("\\s+");
            if (parts.length != ChatFace.SIZE * ChatFace.SIZE) {
                return Optional.empty();
            }
            List<Integer> pixels = new ArrayList<>(parts.length);
            for (String part : parts) {
                pixels.add(Integer.parseInt(part, 16));
            }
            return Optional.of(new ChatFace(pixels, hash, false));
        } catch (IOException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private void toDisk(ChatFace face) {
        if (folder == null) {
            return;
        }
        try {
            Files.createDirectories(folder);
            StringBuilder text = new StringBuilder();
            for (int pixel : face.pixels()) {
                if (!text.isEmpty()) {
                    text.append(' ');
                }
                text.append(String.format("%06x", pixel));
            }
            Path temporary = folder.resolve(face.source() + ".tmp");
            Files.writeString(temporary, text.toString(), StandardCharsets.US_ASCII);
            Files.move(temporary, folder.resolve(face.source() + ".face"), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "Could not store the face " + face.source(), e);
        }
    }
}
