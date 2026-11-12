package net.prismarin.shared.skin;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SkinTextures {

    public static final String HOST = "textures.minecraft.net";
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{16,128}");
    private static final String PATH = "/texture/";
    private static final int MAX_VALUE = 8_192;

    public record Skin(String hash, boolean slim) {

        public URI uri() {
            return SkinTextures.uri(hash);
        }
    }

    private SkinTextures() {
        throw new AssertionError();
    }

    public static Optional<Skin> parse(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_VALUE) {
            return Optional.empty();
        }
        try {
            byte[] decoded = Base64.getMimeDecoder().decode(value.strip());
            JsonObject root = JsonParser.parseString(new String(decoded, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject skin = object(object(root, "textures"), "SKIN");
            if (skin == null) {
                return Optional.empty();
            }
            JsonElement url = skin.get("url");
            if (url == null || !url.isJsonPrimitive()) {
                return Optional.empty();
            }
            JsonObject metadata = object(skin, "metadata");
            JsonElement model = metadata == null ? null : metadata.get("model");
            boolean slim = model != null && model.isJsonPrimitive() && "slim".equalsIgnoreCase(model.getAsString());
            return hash(url.getAsString()).map(hash -> new Skin(hash, slim));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static Optional<String> hash(String url) {
        try {
            URI uri = URI.create(url.strip());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }
            if (uri.getHost() == null || !HOST.equalsIgnoreCase(uri.getHost()) || uri.getUserInfo() != null
                    || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return Optional.empty();
            }
            String path = uri.getRawPath();
            if (path == null || !path.startsWith(PATH)) {
                return Optional.empty();
            }
            String hash = path.substring(PATH.length()).toLowerCase(Locale.ROOT);
            return HASH.matcher(hash).matches() ? Optional.of(hash) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public static URI uri(String hash) {
        if (hash == null || !HASH.matcher(hash).matches()) {
            throw new IllegalArgumentException("no texture hash: " + hash);
        }
        return URI.create("https://" + HOST + PATH + hash);
    }

    public static boolean allowed(URI uri) {
        return uri != null && hash(uri.toString()).map(found -> uri.equals(uri(found))).orElse(false);
    }

    public static String encode(String url, boolean slim) {
        JsonObject skin = new JsonObject();
        skin.addProperty("url", url);
        if (slim) {
            JsonObject metadata = new JsonObject();
            metadata.addProperty("model", "slim");
            skin.add("metadata", metadata);
        }
        JsonObject textures = new JsonObject();
        textures.add("SKIN", skin);
        JsonObject root = new JsonObject();
        root.add("textures", textures);
        return Base64.getEncoder().encodeToString(root.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null) {
            return null;
        }
        JsonElement value = parent.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
