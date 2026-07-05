package net.prismarin.velocity.listener;

import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.locale.ProxyMessages;

public final class MaintenanceLoginListener {

    private static final String BYPASS_PERMISSION = "prismarin.maintenance.bypass";

    private final ProxyConfigStore configStore;
    private final ProxyMessages messages;

    public MaintenanceLoginListener(ProxyConfigStore configStore, ProxyMessages messages) {
        this.configStore = configStore;
        this.messages = messages;
    }

    @Subscribe
    public void onLogin(LoginEvent event) {
        if (!configStore.maintenance()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.hasPermission(BYPASS_PERMISSION)) {
            return;
        }
        event.setResult(ResultedEvent.ComponentResult.denied(messages.render(player, "maintenance.kick")));
    }
}
