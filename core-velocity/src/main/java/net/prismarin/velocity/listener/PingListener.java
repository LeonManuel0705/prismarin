package net.prismarin.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.proxy.server.ServerPing;
import net.prismarin.velocity.config.ProxyConfig;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.motd.MotdBuilder;

public final class PingListener {

    private static final int MAINTENANCE_PROTOCOL = -1;

    private final ProxyConfigStore configStore;
    private final MotdBuilder motdBuilder;

    public PingListener(ProxyConfigStore configStore, MotdBuilder motdBuilder) {
        this.configStore = configStore;
        this.motdBuilder = motdBuilder;
    }

    @Subscribe
    public void onProxyPing(ProxyPingEvent event) {
        ProxyConfig config = configStore.config();
        ServerPing.Builder builder = event.getPing().asBuilder()
                .description(motdBuilder.description(config))
                .maximumPlayers(config.maxPlayers());
        if (config.maintenance()) {
            builder.version(new ServerPing.Version(MAINTENANCE_PROTOCOL, config.maintenanceVersionName()));
        }
        event.setPing(builder.build());
    }
}
