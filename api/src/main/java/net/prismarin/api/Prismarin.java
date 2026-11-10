package net.prismarin.api;

import java.util.Set;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.moderation.PunishmentService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.GameStateService;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatusService;

public interface Prismarin {

    String serverId();

    Branding branding();

    ProfileService profiles();

    EconomyService economy();

    LocaleService locale();

    MessageBus messageBus();

    ServerDirectory servers();

    ServerStatusService serverStatus();

    GameStateService gameState();

    NetworkConnector connector();

    PunishmentService punishments();

    EventBus events();

    Set<String> enabledSystems();

    default boolean systemEnabled(String system) {
        return enabledSystems().contains(system);
    }
}
