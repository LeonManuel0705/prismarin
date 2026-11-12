package net.prismarin.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.shared.config.CoreConfig;

public final class ModuleState {

    private static final Map<String, List<String>> CONFLICTS = Map.of(
            CoreModules.TABLIST, List.of("TAB"),
            CoreModules.SIDEBAR, List.of("FeatherBoard", "AnimatedScoreboard", "SimpleScore"),
            CoreModules.CHAT, List.of("EssentialsChat", "ChatControl", "ChatControlRed", "VentureChat", "LPC",
                    "DeluxeChat", "CarbonChat", "ChatManager"),
            CoreModules.NICK, List.of("EazyNick", "NickNamer", "BetterNick"),
            CoreModules.ESSENTIALS, List.of("Essentials", "CMI"),
            CoreModules.CHAT_FILTER, List.of("ChatSentry", "ChatControl", "ChatControlRed"),
            CoreModules.DAILY_REWARDS, List.of("DailyRewards", "DailyRewardsPlus"),
            CoreModules.VOTE_REWARDS, List.of("VotingPlugin", "SuperbVote"),
            CoreModules.COSMETICS, List.of("UltraCosmetics", "ProCosmetics", "GadgetsMenu", "HMCCosmetics"));

    private static final Map<String, String> REQUIRED_SYSTEMS = Map.of(
            CoreModules.ECONOMY_COMMANDS, PrismarinSystems.ECONOMY,
            CoreModules.VAULT, PrismarinSystems.ECONOMY,
            CoreModules.NETWORK_MENU, PrismarinSystems.NETWORK_STATUS,
            CoreModules.PLAY_COMMAND, PrismarinSystems.NETWORK_STATUS);

    private final Map<String, String> disabled;

    private ModuleState(Map<String, String> disabled) {
        this.disabled = Map.copyOf(disabled);
    }

    public static ModuleState compute(CoreConfig config, Predicate<String> pluginInstalled) {
        Map<String, String> disabled = new LinkedHashMap<>();
        for (String module : CoreModules.ALL) {
            if (!config.moduleEnabled(module)) {
                disabled.put(module, "it is switched off in config.yml");
                continue;
            }
            String system = REQUIRED_SYSTEMS.get(module);
            if (system != null && !config.systemEnabled(system)) {
                disabled.put(module, "the " + system + " system is disabled");
                continue;
            }
            if (config.autoDetectConflicts()) {
                CONFLICTS.getOrDefault(module, List.of()).stream()
                        .filter(pluginInstalled)
                        .findFirst()
                        .ifPresent(plugin -> disabled.put(module, plugin + " is installed"
                                + " (set compatibility.auto-detect to false to keep it)"));
            }
        }
        return new ModuleState(disabled);
    }

    public boolean enabled(String module) {
        return !disabled.containsKey(module);
    }

    public Map<String, String> disabledReasons() {
        return disabled;
    }

    public List<String> enabledModules() {
        return CoreModules.ALL.stream().filter(this::enabled).toList();
    }
}
