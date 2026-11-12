package net.prismarin.core.social;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import net.prismarin.api.social.ClanService;
import net.prismarin.api.social.FriendService;
import net.prismarin.api.social.PartyService;
import net.prismarin.api.social.SocialApi;
import net.prismarin.core.PrismarinCorePlugin;
import org.bukkit.plugin.ServicePriority;

@Singleton
public final class BackendSocial {

    private final PrismarinCorePlugin plugin;
    private final BackendFriendService friends;
    private final BackendPartyService parties;
    private final BackendClanService clans;

    @Inject
    public BackendSocial(PrismarinCorePlugin plugin, BackendFriendService friends, BackendPartyService parties,
                         BackendClanService clans) {
        this.plugin = plugin;
        this.friends = friends;
        this.parties = parties;
        this.clans = clans;
    }

    public void register() {
        SocialApi.register(friends, parties, clans);
        plugin.getServer().getServicesManager().register(FriendService.class, friends, plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(PartyService.class, parties, plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(ClanService.class, clans, plugin, ServicePriority.Normal);
    }
}
