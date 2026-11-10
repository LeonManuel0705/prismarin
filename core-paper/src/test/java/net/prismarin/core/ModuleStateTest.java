package net.prismarin.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.Currency;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.config.SqlSettings;
import org.junit.jupiter.api.Test;

class ModuleStateTest {

    private static CoreConfig config(Map<String, Boolean> modules, Map<String, Boolean> systems, boolean detect) {
        return new CoreConfig("lobby", CoreConfig.StorageMode.LOCAL, "", "", false, "", 0, "", "de",
                Branding.DEFAULT, List.of(Currency.COINS), Currency.COINS, modules, systems, detect,
                SqlSettings.DEFAULT);
    }

    @Test
    void everythingIsOnByDefault() {
        ModuleState state = ModuleState.compute(config(Map.of(), Map.of(), true), name -> false);
        assertEquals(CoreModules.ALL, state.enabledModules());
    }

    @Test
    void conflictingPluginSwitchesModuleOff() {
        ModuleState state = ModuleState.compute(config(Map.of(), Map.of(), true), Set.of("TAB", "LPC")::contains);
        assertFalse(state.enabled(CoreModules.TABLIST));
        assertFalse(state.enabled(CoreModules.CHAT));
        assertTrue(state.enabled(CoreModules.SIDEBAR));
        assertTrue(state.disabledReasons().get(CoreModules.TABLIST).startsWith("TAB is installed"));
    }

    @Test
    void autoDetectCanBeTurnedOff() {
        ModuleState state = ModuleState.compute(config(Map.of(), Map.of(), false), Set.of("TAB")::contains);
        assertTrue(state.enabled(CoreModules.TABLIST));
    }

    @Test
    void disabledSystemTakesDependentModulesWithIt() {
        ModuleState state = ModuleState.compute(config(Map.of(), Map.of("economy", false), true), name -> false);
        assertFalse(state.enabled(CoreModules.ECONOMY_COMMANDS));
        assertFalse(state.enabled(CoreModules.VAULT));
        assertEquals("the economy system is disabled", state.disabledReasons().get(CoreModules.VAULT));
        assertTrue(state.enabled(CoreModules.NETWORK_MENU));
    }

    @Test
    void configSwitchWins() {
        ModuleState state = ModuleState.compute(config(Map.of("vault", false), Map.of(), true), name -> false);
        assertEquals("it is switched off in config.yml", state.disabledReasons().get(CoreModules.VAULT));
    }
}
