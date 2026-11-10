package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;

public final class MessageService {

    private record Reply(UUID partner, boolean incoming) {
    }

    private final SocialContext context;
    private final Map<UUID, Reply> replies = new ConcurrentHashMap<>();

    public MessageService(SocialContext context) {
        this.context = context;
    }

    public String replyCommand() {
        return "/" + context.settings().messages().replyCommands().getFirst();
    }

    public CompletableFuture<Void> message(Player sender, String input, String text) {
        return context.async(() -> {
            Optional<Player> target = context.findVisible(sender, context.friendsOf(sender), input);
            if (target.isEmpty()) {
                context.send(sender, "msg.not-online", Placeholder.unparsed("player", input));
                return;
            }
            if (target.get().getUniqueId().equals(sender.getUniqueId())) {
                context.send(sender, "msg.self");
                return;
            }
            deliver(sender, target.get(), text);
        });
    }

    public CompletableFuture<Void> reply(Player sender, String text) {
        return context.async(() -> {
            Reply reply = replies.get(sender.getUniqueId());
            if (reply == null) {
                context.send(sender, "msg.no-reply");
                return;
            }
            Optional<Player> target = context.proxy().getPlayer(reply.partner())
                    .filter(found -> reply.incoming() || context.visibleTo(sender, found));
            if (target.isEmpty()) {
                context.send(sender, "msg.reply-offline");
                return;
            }
            deliver(sender, target.get(), text);
        });
    }

    private void deliver(Player sender, Player target, String text) {
        boolean muted = context.muted(sender.getUniqueId()).join();
        if (muted) {
            context.send(sender, "msg.muted");
            return;
        }
        SocialProfile senderProfile = context.store().cached(sender.getUniqueId());
        SocialProfile targetProfile = context.store().cached(target.getUniqueId());
        String shownTarget = context.visibleName(sender, senderProfile.friendIds(), target);
        String privacy = context.setting(targetProfile, SocialSetting.MESSAGES);
        boolean allowed = SocialSetting.EVERYONE.equals(privacy)
                || SocialSetting.FRIENDS.equals(privacy) && targetProfile.friendsWith(sender.getUniqueId())
                || sender.hasPermission(SocialContext.MESSAGE_BYPASS)
                || replyTo(sender, target);
        if (!allowed) {
            context.send(sender, "msg.blocked", Placeholder.unparsed("player", shownTarget));
            return;
        }
        String shownSender = context.visibleName(target, targetProfile.friendIds(), sender);
        context.send(sender, "msg.sent", Placeholder.unparsed("player", shownTarget),
                Placeholder.unparsed("message", text));
        target.sendMessage(context.render(target, "msg.received", Placeholder.unparsed("player", shownSender),
                        Placeholder.unparsed("message", text))
                .clickEvent(ClickEvent.suggestCommand(replyCommand() + " "))
                .hoverEvent(HoverEvent.showText(context.render(target, "msg.reply-hover",
                        Placeholder.unparsed("player", shownSender)))));
        replies.put(target.getUniqueId(), new Reply(sender.getUniqueId(), true));
        replies.put(sender.getUniqueId(), new Reply(target.getUniqueId(), false));
        spy(sender, target, text);
    }

    private boolean replyTo(Player sender, Player target) {
        Reply reply = replies.get(sender.getUniqueId());
        return reply != null && reply.incoming() && reply.partner().equals(target.getUniqueId());
    }

    private void spy(Player sender, Player target, String text) {
        if (!context.settings().messages().staffSpy()) {
            return;
        }
        for (UUID spy : context.spies()) {
            if (spy.equals(sender.getUniqueId()) || spy.equals(target.getUniqueId())) {
                continue;
            }
            context.proxy().getPlayer(spy).filter(online -> online.hasPermission(SocialContext.SPY))
                    .ifPresent(online -> context.send(online, "msg.spy",
                            Placeholder.unparsed("sender", sender.getUsername()),
                            Placeholder.unparsed("target", target.getUsername()),
                            Placeholder.unparsed("message", text)));
        }
    }

    public void toggleSpy(Player player) {
        if (!context.settings().messages().staffSpy()) {
            context.send(player, "msg.spy-disabled");
            return;
        }
        if (!player.hasPermission(SocialContext.SPY)) {
            context.send(player, "general.no-permission");
            return;
        }
        boolean on = context.spies().add(player.getUniqueId());
        if (!on) {
            context.spies().remove(player.getUniqueId());
        }
        context.send(player, on ? "msg.spy-on" : "msg.spy-off");
    }

    public void forget(UUID player) {
        replies.remove(player);
        context.spies().remove(player);
    }
}
