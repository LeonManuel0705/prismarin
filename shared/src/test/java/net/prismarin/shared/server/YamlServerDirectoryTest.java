package net.prismarin.shared.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.prismarin.api.server.NetworkServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class YamlServerDirectoryTest {

    @TempDir
    Path directory;

    @Test
    void readsGroupsAndProperties() throws IOException {
        Path file = directory.resolve("servers.yml");
        Files.writeString(file, """
                servers:
                  bw-1:
                    display-name: "&cBedwars 1"
                    port: 25570
                    group: Bedwars
                    icon: RED_BED
                    description: "4x2 Teams"
                """);
        NetworkServer server = new YamlServerDirectory(file).byName("BW-1").orElseThrow();
        assertEquals("bedwars", server.group());
        assertEquals(Optional.of("RED_BED"), server.property("icon"));
        assertEquals(Optional.of("4x2 Teams"), server.property("description"));
        assertTrue(server.property("port").isEmpty());
    }

    @Test
    void legacyCategoriesMapToGroups() throws IOException {
        Path file = directory.resolve("servers.yml");
        Files.writeString(file, """
                servers:
                  lobby:
                    category: LOBBY
                  creative:
                    category: BUILD
                  other: {}
                """);
        YamlServerDirectory servers = new YamlServerDirectory(file);
        assertEquals(NetworkServer.GROUP_LOBBY, servers.byName("lobby").orElseThrow().group());
        assertEquals("build", servers.byName("creative").orElseThrow().group());
        assertEquals(NetworkServer.GROUP_DEFAULT, servers.byName("other").orElseThrow().group());
        assertEquals(1, servers.byGroup("LOBBY").size());
    }

    @Test
    void missingFileWithoutBundledDefaultsIsEmpty() {
        assertTrue(new YamlServerDirectory(directory.resolve("none.yml")).servers().isEmpty());
    }
}
