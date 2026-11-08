package net.prismarin.shared.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConfigLoaderTest {

    @TempDir
    Path directory;

    @Test
    void freshFileGetsAllDefaults() throws IOException {
        Path file = directory.resolve("config.yml");
        CoreConfig config = ConfigLoader.load(file, List.of("chat", "tablist"));
        assertEquals(Branding.DEFAULT, config.branding());
        assertEquals(List.of(Currency.COINS, Currency.GEMS), config.currencies());
        assertEquals(Currency.COINS, config.primaryCurrency());
        assertTrue(config.moduleEnabled("chat"));
        String written = Files.readString(file);
        assertTrue(written.contains("branding:"));
        assertTrue(written.contains("tablist: true"));
    }

    @Test
    void legacyFileKeepsValuesAndGainsNewSections() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "server-id: lobby\nstorage:\n  mode: LOCAL\n");
        CoreConfig config = ConfigLoader.load(file, List.of("chat"));
        assertEquals("lobby", config.serverId());
        assertTrue(Files.readString(file).contains("economy:"));
        assertTrue(Files.readString(file).contains("server-id: lobby"));
    }

    @Test
    void customCurrenciesModulesAndBranding() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, """
                branding:
                  name: SKYNET
                  primary-color: "#FF0000"
                  accent-color: "#00FF00"
                  prefix: "&8[%brand%&8] &7"
                economy:
                  primary: tokens
                  currencies: [tokens, Shards]
                modules:
                  chat: false
                """);
        CoreConfig config = ConfigLoader.load(file, List.of("chat", "tablist"));
        assertEquals("SKYNET", config.branding().name());
        assertEquals(List.of(Currency.of("tokens"), Currency.of("shards")), config.currencies());
        assertEquals(Currency.of("tokens"), config.primaryCurrency());
        assertFalse(config.moduleEnabled("chat"));
        assertTrue(config.moduleEnabled("tablist"));
    }

    @Test
    void primaryCurrencyMustBeDeclared() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "economy:\n  primary: gold\n  currencies: [coins]\n");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ConfigLoader.load(file, List.of()));
        assertTrue(error.getMessage().contains("gold"));
    }

    @Test
    void invalidBrandColorIsRejectedWithFileName() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "branding:\n  primary-color: blue\n");
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ConfigLoader.load(file, List.of()));
        assertTrue(error.getMessage().startsWith("Invalid config.yml"));
    }

    @Test
    void systemsDefaultToEnabledAndCanBeSwitchedOff() throws IOException {
        Path file = directory.resolve("config.yml");
        Files.writeString(file, "systems:\n  economy: false\n");
        CoreConfig config = ConfigLoader.load(file, List.of());
        assertFalse(config.systemEnabled("economy"));
        assertTrue(config.systemEnabled("levels"));
        assertTrue(config.systemEnabled("network-status"));
        assertTrue(Files.readString(file).contains("network-status: true"));
    }
}
