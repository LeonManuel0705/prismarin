package net.prismarin.velocity.social;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.social.ClanAccess;
import net.prismarin.api.social.ClanRole;
import net.prismarin.shared.social.ClanColors;

public final class ClanCommands {

    public static final String ACCESS = "access";
    private static final List<String> ACCESS_OPTIONS = List.of(ClanAccess.INVITE_ONLY.id(), ClanAccess.OPEN.id());

    private ClanCommands() {
        throw new AssertionError();
    }

    public static List<String> visible(SocialContext context, Player player) {
        SocialSettings.Clans settings = context.settings().clans();
        List<String> visible = new ArrayList<>();
        for (String action : settings.subCommands().visible()) {
            if ("color".equals(action) && !permitted(player, settings.colorPermission())
                    || "create".equals(action) && !permitted(player, settings.createPermission())) {
                continue;
            }
            visible.add(action);
        }
        return List.copyOf(visible);
    }

    private static boolean permitted(Player player, String permission) {
        return permission.isEmpty() || player.hasPermission(permission);
    }

    public static SimpleCommand clan(SocialContext context, ClanService clans, FriendService friends) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                Player player = SocialCommands.player(context, invocation.source());
                if (player == null) {
                    return;
                }
                String[] args = invocation.arguments();
                String label = "/" + invocation.alias();
                SubCommands sub = clans.settings().subCommands();
                if (args.length == 0) {
                    clans.info(player, null);
                    return;
                }
                String action = args[0].toLowerCase(Locale.ROOT);
                if (sub.actions().contains(action) && !visible(context, player).contains(action)) {
                    SocialCommands.help(context, player, "clan", label, visible(context, player));
                    return;
                }
                switch (action) {
                    case "create" -> {
                        if (args.length != 3) {
                            context.send(player, "clan.usage-create", Placeholder.unparsed("command", label));
                            return;
                        }
                        clans.create(player, args[1], args[2]);
                    }
                    case "info" -> clans.info(player, args.length > 1 ? String.join(" ",
                            Arrays.copyOfRange(args, 1, args.length)) : null);
                    case "invite" -> SocialCommands.named(context, player, args, "clan.usage-invite", label,
                            name -> clans.invite(player, name));
                    case "accept" -> clans.accept(player, args.length > 1 ? args[1] : null);
                    case "deny" -> clans.deny(player, args.length > 1 ? args[1] : null);
                    case "join" -> SocialCommands.named(context, player, args, "clan.usage-join", label,
                            name -> clans.join(player, name));
                    case "leave" -> clans.leave(player);
                    case "kick" -> SocialCommands.named(context, player, args, "clan.usage-kick", label,
                            name -> clans.kick(player, name));
                    case "promote" -> SocialCommands.named(context, player, args, "clan.usage-promote", label,
                            name -> clans.promote(player, name));
                    case "demote" -> SocialCommands.named(context, player, args, "clan.usage-demote", label,
                            name -> clans.demote(player, name));
                    case "transfer" -> SocialCommands.named(context, player, args, "clan.usage-transfer", label,
                            name -> clans.transfer(player, name));
                    case "disband" -> clans.disband(player, args.length > 1 && args[1].equalsIgnoreCase("confirm"));
                    case "list" -> clans.list(player, args.length > 1 ? SocialCommands.page(args[1]) : 0);
                    case "chat" -> {
                        if (args.length < 2) {
                            context.send(player, "clan.usage-chat", Placeholder.unparsed("command",
                                    "/" + clans.settings().chatCommands().getFirst()));
                            return;
                        }
                        clans.chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    }
                    case "color" -> SocialCommands.named(context, player, args, "clan.usage-color", label,
                            color -> clans.color(player, color));
                    case "rename" -> SocialCommands.named(context, player, args, "clan.usage-rename", label,
                            name -> clans.rename(player, name, null));
                    case "tag" -> SocialCommands.named(context, player, args, "clan.usage-tag", label,
                            tag -> clans.rename(player, null, tag));
                    case "settings" -> settings(context, clans, friends, player, args, label);
                    default -> SocialCommands.help(context, player, "clan", label, visible(context, player));
                }
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    return CompletableFuture.completedFuture(List.of());
                }
                String[] args = invocation.arguments();
                List<String> visible = visible(context, player);
                if (args.length <= 1) {
                    return CompletableFuture.completedFuture(FriendService.filter(visible,
                            args.length == 0 ? "" : args[0]));
                }
                String action = args[0].toLowerCase(Locale.ROOT);
                String prefix = args[args.length - 1];
                if (!visible.contains(action) || args.length > 2 && !"settings".equals(action)) {
                    return CompletableFuture.completedFuture(List.of());
                }
                return context.supply(() -> switch (action) {
                    case "invite" -> context.visibleNames(player, context.friendsOf(player), prefix, false);
                    case "accept", "deny" -> clans.invitingNames(player, prefix);
                    case "join" -> joinSuggestions(clans, player, prefix);
                    case "info" -> clans.clanNames(prefix);
                    case "kick" -> clans.memberNames(player, prefix, true, role -> role != ClanRole.LEADER);
                    case "promote" -> clans.memberNames(player, prefix, false,
                            role -> role == ClanRole.MEMBER || role == ClanRole.MODERATOR);
                    case "demote" -> clans.memberNames(player, prefix, false,
                            role -> role == ClanRole.MODERATOR || role == ClanRole.CO_LEADER);
                    case "transfer" -> clans.memberNames(player, prefix, false, role -> true);
                    case "disband" -> FriendService.filter(List.of("confirm"), prefix);
                    case "color" -> FriendService.filter(ClanColors.suggestions(), prefix);
                    case "settings" -> settingSuggestions(context, args);
                    default -> List.<String>of();
                }).exceptionally(error -> List.of());
            }
        };
    }

    public static SimpleCommand clanChat(SocialContext context, ClanService clans) {
        return invocation -> {
            Player player = SocialCommands.player(context, invocation.source());
            if (player == null) {
                return;
            }
            if (invocation.arguments().length == 0) {
                context.send(player, "clan.usage-chat", Placeholder.unparsed("command", "/" + invocation.alias()));
                return;
            }
            clans.chat(player, String.join(" ", invocation.arguments()));
        };
    }

    private static void settings(SocialContext context, ClanService clans, FriendService friends, Player player,
                                 String[] args, String label) {
        if (args.length == 1) {
            clans.showSettings(player).thenRun(() -> {
                if (context.personalSettings()) {
                    friends.showSettings(player, label);
                }
            });
            return;
        }
        if (!ACCESS.equalsIgnoreCase(args[1])) {
            if (context.personalSettings()) {
                SocialCommands.settings(context, friends, player, args, label);
            } else {
                context.send(player, "clan.usage-settings", Placeholder.unparsed("command", label));
            }
            return;
        }
        if (args.length != 3) {
            context.send(player, "clan.usage-settings", Placeholder.unparsed("command", label));
            return;
        }
        Optional<ClanAccess> access = ClanAccess.byId(args[2]);
        if (access.isEmpty()) {
            context.send(player, "friends.invalid-value", Placeholder.unparsed("value", args[2]),
                    Placeholder.unparsed("options", String.join(", ", ACCESS_OPTIONS)));
            return;
        }
        clans.access(player, access.get());
    }

    private static List<String> settingSuggestions(SocialContext context, String[] args) {
        if (args.length == 2) {
            List<String> keys = new ArrayList<>(List.of(ACCESS));
            if (context.personalSettings()) {
                keys.addAll(SocialCommands.settingSuggestions(context, args));
            }
            return FriendService.filter(keys, args[1]);
        }
        if (args.length == 3 && ACCESS.equalsIgnoreCase(args[1])) {
            return FriendService.filter(ACCESS_OPTIONS, args[2]);
        }
        return context.personalSettings() ? SocialCommands.settingSuggestions(context, args) : List.of();
    }

    private static List<String> joinSuggestions(ClanService clans, Player player, String prefix) {
        Set<String> names = new LinkedHashSet<>(clans.invitingNames(player, prefix));
        names.addAll(clans.clanNames(prefix));
        return List.copyOf(names);
    }
}
