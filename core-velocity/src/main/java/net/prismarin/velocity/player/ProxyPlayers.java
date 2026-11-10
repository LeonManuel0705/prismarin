package net.prismarin.velocity.player;

import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import java.util.concurrent.Executor;
import net.prismarin.api.player.PlayerApi;
import net.prismarin.api.player.PlayerService;
import net.prismarin.velocity.admin.AdminContext;

public final class ProxyPlayers {

    private ProxyPlayers() {
        throw new AssertionError();
    }

    public static PlayerService register(Object plugin, AdminContext admin, Executor executor) {
        ProxyPlayerService service = new ProxyPlayerService(admin.proxy(), admin.services(), admin.messages(),
                admin.vanish(), new ProxyNickClaims(admin.services().documents(), executor), admin.channel(),
                executor);
        admin.proxy().getEventManager().register(plugin, service);
        admin.channel().handlePlayers(service);
        admin.messages().players().onChange(service::localeChanged);
        PlayerApi.register(service);
        admin.proxy().getEventManager().register(plugin, ProxyShutdownEvent.class, event -> PlayerApi.unregister());
        return service;
    }
}
