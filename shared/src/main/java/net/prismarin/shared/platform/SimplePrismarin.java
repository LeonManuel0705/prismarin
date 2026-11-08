package net.prismarin.shared.platform;

import java.util.Set;
import net.prismarin.api.Prismarin;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatusService;

public record SimplePrismarin(String serverId, Branding branding, ProfileService profiles, EconomyService economy,
                              LocaleService locale, MessageBus messageBus, ServerDirectory servers,
                              ServerStatusService serverStatus, EventBus events, Set<String> enabledSystems) implements Prismarin {

    public SimplePrismarin {
        enabledSystems = Set.copyOf(enabledSystems);
    }
}
