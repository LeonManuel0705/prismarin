package net.prismarin.shared.storage;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public final class JsonFileDocumentStore implements DocumentStore {

    private static final Pattern COLLECTION = Pattern.compile("[a-z0-9_-]{1,64}");
    private static final String SUFFIX = ".json";
    private static final int MAX_HEX_NAME = 200;

    private final Path directory;

    public JsonFileDocumentStore(Path dataDirectory) {
        this.directory = dataDirectory.resolve("documents");
    }

    @Override
    public synchronized void save(String collection, String id, String json) {
        Path file = fileFor(collection, id);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, json, StandardCharsets.UTF_8);
            Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized Optional<String> find(String collection, String id) {
        Path file = fileFor(collection, id);
        if (Files.notExists(file)) {
            return Optional.empty();
        }
        return Optional.of(read(file));
    }

    @Override
    public synchronized List<String> findAll(String collection) {
        Path folder = folderFor(collection);
        if (Files.notExists(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            List<String> documents = new ArrayList<>();
            files.filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .forEach(file -> documents.add(read(file)));
            return documents;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized Map<String, String> entries(String collection) {
        Path folder = folderFor(collection);
        if (Files.notExists(folder)) {
            return Map.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            Map<String, String> entries = new LinkedHashMap<>();
            files.filter(file -> file.getFileName().toString().endsWith(SUFFIX))
                    .sorted()
                    .forEach(file -> {
                        String name = file.getFileName().toString();
                        String stem = name.substring(0, name.length() - SUFFIX.length());
                        if (!stem.startsWith("h")) {
                            entries.put(new String(HexFormat.of().parseHex(stem), StandardCharsets.UTF_8), read(file));
                        }
                    });
            return entries;
        } catch (IOException | IllegalArgumentException e) {
            throw new IllegalStateException("could not list documents in " + collection, e);
        }
    }

    @Override
    public synchronized List<String> findBy(String collection, String field, String value) {
        return findAll(collection).stream().filter(json -> matches(json, field, value)).toList();
    }

    @Override
    public synchronized boolean delete(String collection, String id) {
        try {
            return Files.deleteIfExists(fileFor(collection, id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void close() {
    }

    static boolean matches(String json, String field, String value) {
        JsonElement root = JsonParser.parseString(json);
        if (!root.isJsonObject()) {
            return false;
        }
        JsonObject object = root.getAsJsonObject();
        JsonElement element = object.get(field);
        return element != null && element.isJsonPrimitive() && element.getAsString().equals(value);
    }

    private Path folderFor(String collection) {
        if (collection == null || !COLLECTION.matcher(collection).matches()) {
            throw new IllegalArgumentException("invalid collection name: " + collection);
        }
        return directory.resolve(collection);
    }

    private Path fileFor(String collection, String id) {
        if (id == null || id.isEmpty() || id.length() > 128) {
            throw new IllegalArgumentException("invalid document id");
        }
        String hex = HexFormat.of().formatHex(id.getBytes(StandardCharsets.UTF_8));
        return folderFor(collection).resolve((hex.length() > MAX_HEX_NAME ? "h" + sha256(id) : hex) + SUFFIX);
    }

    private static String sha256(String id) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
