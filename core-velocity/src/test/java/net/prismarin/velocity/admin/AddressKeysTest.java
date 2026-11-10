package net.prismarin.velocity.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;
import net.prismarin.shared.storage.JsonFileDocumentStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AddressKeysTest {

    @TempDir
    Path directory;

    @Test
    void ipv6AddressesShareTheirNetworkPrefix() throws UnknownHostException {
        assertEquals("203.0.113.7", AddressKeys.normalize(InetAddress.getByName("203.0.113.7")));
        String first = AddressKeys.normalize(InetAddress.getByName("2001:db8:1:2:aaaa::1"));
        String second = AddressKeys.normalize(InetAddress.getByName("2001:db8:1:2:bbbb::9"));
        assertEquals("20010db800010002/64", first);
        assertEquals(first, second);
        assertNotEquals(first, AddressKeys.normalize(InetAddress.getByName("2001:db8:1:3::1")));
    }

    @Test
    void hashesNeedTheSalt() {
        String hash = AddressKeys.hash("salt", "203.0.113.7");
        assertEquals(64, hash.length());
        assertEquals(hash, AddressKeys.hash("salt", "203.0.113.7"));
        assertNotEquals(hash, AddressKeys.hash("pepper", "203.0.113.7"));
    }

    @Test
    void everyProxyOnTheSameStorageUsesTheSameSalt() throws UnknownHostException {
        JsonFileDocumentStore store = new JsonFileDocumentStore(directory);
        InetSocketAddress address = new InetSocketAddress(InetAddress.getByName("198.51.100.4"), 50000);
        String first = new AddressKeys(store).of(address).orElseThrow();
        String second = new AddressKeys(store).of(new InetSocketAddress(address.getAddress(), 61000)).orElseThrow();
        assertEquals(first, second);
        assertTrue(new AddressKeys(store).of(null).isEmpty());
    }
}
