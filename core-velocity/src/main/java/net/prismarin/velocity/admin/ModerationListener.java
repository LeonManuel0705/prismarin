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

    public ModerationListener(AdminContext context) {
        this.context = context;
    }

    @Subscribe
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.resumeWhenComplete(context.services().punishments()
                .active(player.getUniqueId(), PunishmentType.BAN)
                .thenAccept(ban -> ban.ifPresent(punishment -> event.setResult(ResultedEvent.ComponentResult.denied(
                        ModerationCommands.banScreen(context, player, punishment)))))
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
                .thenAccept(mute -> mute.ifPresent(punishment ->
                        context.channel().sendMute(player, punishment.expiresAt(), punishment.reason())));
    }
}
