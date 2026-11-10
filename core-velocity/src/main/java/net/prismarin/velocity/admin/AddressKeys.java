package net.prismarin.velocity.admin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Optional;
import net.prismarin.shared.storage.DocumentStore;

public final class AddressKeys {

    static final String COLLECTION = "punishment-meta";
    static final String SALT_ID = "address-salt";
    private static final int IPV6_PREFIX_BYTES = 8;

    private final DocumentStore documents;
    private volatile String salt;

    public AddressKeys(DocumentStore documents) {
        this.documents = documents;
    }

    public Optional<String> of(InetSocketAddress address) {
        if (address == null || address.getAddress() == null) {
            return Optional.empty();
        }
        return Optional.of(hash(salt(), normalize(address.getAddress())));
    }

    static String normalize(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            byte[] prefix = new byte[IPV6_PREFIX_BYTES];
            System.arraycopy(bytes, 0, prefix, 0, IPV6_PREFIX_BYTES);
            return HexFormat.of().formatHex(prefix) + "/64";
        }
        return address.getHostAddress();
    }

    static String hash(String salt, String address) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest((salt + "|" + address).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String salt() {
        String known = salt;
        if (known != null) {
            return known;
        }
        synchronized (this) {
            if (salt == null) {
                salt = stored().orElseGet(this::create);
            }
            return salt;
        }
    }

    private Optional<String> stored() {
        return documents.find(COLLECTION, SALT_ID)
                .map(json -> JsonParser.parseString(json).getAsJsonObject())
                .filter(object -> object.has("salt"))
                .map(object -> object.get("salt").getAsString())
                .filter(value -> !value.isBlank());
    }

    private String create() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        JsonObject object = new JsonObject();
        object.addProperty("salt", HexFormat.of().formatHex(bytes));
        documents.save(COLLECTION, SALT_ID, object.toString());
        return stored().orElseThrow();
    }
}
