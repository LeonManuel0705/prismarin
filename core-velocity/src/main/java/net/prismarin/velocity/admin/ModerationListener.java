package net.prismarin.velocity.admin;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.prismarin.api.moderation.PunishmentType;

public final class ModerationListener {

    private final AdminContext context;
    private final PunishmentFlow flow;

    public ModerationListener(AdminContext context, PunishmentFlow flow) {
        this.context = context;
        this.flow = flow;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(flow.loginBlock(player)
                .thenAccept(block -> block.ifPresent(screen ->
                        event.setResult(ResultedEvent.ComponentResult.denied(screen))))
                .exceptionally(error -> null));
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        context.services().rememberName(event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
    }

    @Subscribe
    public void onServerConnected(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        context.services().punishments().active(player.getUniqueId(), PunishmentType.MUTE)
                .thenAccept(mute -> mute.ifPresent(punishment -> flow.muteOn(player, punishment)));
    }
}
