package net.prismarin.velocity.social;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.social.PartyRole;

public final class SocialCommands {

    private static final List<String> FRIEND_ACTIONS = List.of("add", "accept", "deny", "cancel", "remove", "list",
            "requests", "jump", "settings", "help");
    private static final List<String> PARTY_ACTIONS = List.of("invite", "accept", "deny", "join", "leave", "kick",
            "promote", "demote", "transfer", "disband", "list", "warp", "chat", "settings", "help");

    private SocialCommands() {
        throw new AssertionError();
    }

    public static SimpleCommand friend(SocialContext context, FriendService friends) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                Player player = player(context, invocation.source());
                if (player == null) {
                    return;
                }
                String[] args = invocation.arguments();
                String label = "/" + invocation.alias();
                if (args.length == 0) {
                    help(context, player, "friends", label, FRIEND_ACTIONS);
                    return;
                }
                String action = args[0].toLowerCase(Locale.ROOT);
                switch (action) {
                    case "add" -> named(context, player, args, "friends.usage-add", label,
                            name -> friends.add(player, name));
                    case "accept" -> named(context, player, args, "friends.usage-accept", label,
                            name -> friends.accept(player, name));
                    case "deny" -> named(context, player, args, "friends.usage-deny", label,
                            name -> friends.deny(player, name));
                    case "cancel" -> named(context, player, args, "friends.usage-cancel", label,
                            name -> friends.cancel(player, name));
                    case "remove" -> named(context, player, args, "friends.usage-remove", label,
                            name -> friends.remove(player, name));
                    case "jump" -> named(context, player, args, "friends.usage-jump", label,
                            name -> friends.jump(player, name));
                    case "list" -> friends.list(player, args.length > 1 ? page(args[1]) : 0);
                    case "requests" -> friends.showRequests(player);
                    case "settings" -> settings(context, friends, player, args, label);
                    case "help" -> help(context, player, "friends", label, FRIEND_ACTIONS);
                    default -> {
                        if (args.length == 1 && FriendService.validName(args[0])) {
                            friends.add(player, args[0]);
                        } else {
                            help(context, player, "friends", label, FRIEND_ACTIONS);
                        }
                    }
                }
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    return CompletableFuture.completedFuture(List.of());
                }
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    return CompletableFuture.completedFuture(FriendService.filter(FRIEND_ACTIONS,
                            args.length == 0 ? "" : args[0]));
                }
                String prefix = args[args.length - 1];
                if (args.length > 2 && !args[0].equalsIgnoreCase("settings")) {
                    return CompletableFuture.completedFuture(List.of());
                }
                return context.supply(() -> switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "add" -> {
                        List<String> known = friends.friendNames(player, "", false);
                        yield context.visibleNames(player, context.friendsOf(player), prefix, false, false).stream()
                                .filter(name -> known.stream().noneMatch(name::equalsIgnoreCase))
                                .toList();
                    }
                    case "accept", "deny" -> friends.incomingNames(player, prefix);
                    case "cancel" -> friends.outgoingNames(player, prefix);
                    case "remove" -> friends.friendNames(player, prefix, false);
                    case "jump" -> friends.friendNames(player, prefix, true);
                    case "settings" -> settingSuggestions(context, args);
                    default -> List.<String>of();
                }).exceptionally(error -> List.of());
            }
        };
    }

    public static SimpleCommand party(SocialContext context, PartyService party, FriendService friends) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                Player player = player(context, invocation.source());
                if (player == null) {
                    return;
                }
                String[] args = invocation.arguments();
                String label = "/" + invocation.alias();
                if (args.length == 0) {
                    if (party.parties().partyOf(player.getUniqueId()).isPresent()) {
                        party.list(player);
                    } else {
                        help(context, player, "party", label, PARTY_ACTIONS);
                    }
                    return;
                }
                String action = args[0].toLowerCase(Locale.ROOT);
                switch (action) {
                    case "invite" -> named(context, player, args, "party.usage-invite", label,
                            name -> party.invite(player, name));
                    case "accept" -> party.accept(player, args.length > 1 ? args[1] : null);
                    case "deny" -> party.deny(player, args.length > 1 ? args[1] : null);
                    case "join" -> named(context, player, args, "party.usage-join", label,
                            name -> party.join(player, name));
                    case "leave" -> party.leave(player);
                    case "kick" -> named(context, player, args, "party.usage-kick", label,
                            name -> party.kick(player, name));
                    case "promote" -> named(context, player, args, "party.usage-promote", label,
                            name -> party.promote(player, name));
                    case "demote" -> named(context, player, args, "party.usage-demote", label,
                            name -> party.demote(player, name));
                    case "transfer" -> named(context, player, args, "party.usage-transfer", label,
                            name -> party.transfer(player, name));
                    case "disband" -> party.disband(player);
                    case "list", "info" -> party.list(player);
                    case "warp" -> party.warp(player);
                    case "chat" -> {
                        if (args.length < 2) {
                            context.send(player, "party.usage-chat",
                                    Placeholder.unparsed("command", "/" + context.settings().party().chatCommands()
                                            .getFirst()));
                            return;
                        }
                        party.chat(player, String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                    }
                    case "settings" -> partySettings(context, party, friends, player, args, label);
                    case "help" -> help(context, player, "party", label, PARTY_ACTIONS);
                    default -> {
                        if (args.length == 1 && FriendService.validName(args[0])) {
                            party.invite(player, args[0]);
                        } else {
                            help(context, player, "party", label, PARTY_ACTIONS);
                        }
                    }
                }
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                if (!(invocation.source() instanceof Player player)) {
                    return CompletableFuture.completedFuture(List.of());
                }
                String[] args = invocation.arguments();
                if (args.length <= 1) {
                    return CompletableFuture.completedFuture(FriendService.filter(PARTY_ACTIONS,
                            args.length == 0 ? "" : args[0]));
                }
                String prefix = args[args.length - 1];
                if (args.length > 2 && !args[0].equalsIgnoreCase("settings")) {
                    return CompletableFuture.completedFuture(List.of());
                }
                return context.supply(() -> switch (args[0].toLowerCase(Locale.ROOT)) {
                    case "invite" -> context.visibleNames(player, context.friendsOf(player), prefix, false);
                    case "accept", "deny" -> party.inviterNames(player, prefix);
                    case "join" -> joinSuggestions(context, party, player, prefix);
                    case "kick" -> party.memberNames(player, prefix, true);
                    case "promote" -> party.memberNames(player, prefix, false, PartyRole.MEMBER);
                    case "demote" -> party.memberNames(player, prefix, false, PartyRole.MODERATOR);
                    case "transfer" -> party.memberNames(player, prefix, false);
                    case "settings" -> partySettingSuggestions(context, args);
                    default -> List.<String>of();
                }).exceptionally(error -> List.of());
            }
        };
    }

    public static SimpleCommand partyChat(SocialContext context, PartyService party) {
        return invocation -> {
            Player player = player(context, invocation.source());
            if (player == null) {
                return;
            }
            if (invocation.arguments().length == 0) {
                context.send(player, "party.usage-chat", Placeholder.unparsed("command", "/" + invocation.alias()));
                return;
            }
            party.chat(player, String.join(" ", invocation.arguments()));
        };
    }

    public static SimpleCommand message(SocialContext context, MessageService messages) {
        return new SimpleCommand() {
            @Override
            public void execute(Invocation invocation) {
                Player player = player(context, invocation.source());
                if (player == null) {
                    return;
                }
                String[] args = invocation.arguments();
                if (args.length < 2) {
                    context.send(player, "msg.usage", Placeholder.unparsed("command", "/" + invocation.alias()));
                    return;
                }
                messages.message(player, args[0], String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
            }

            @Override
            public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
                String[] args = invocation.arguments();
                if (!(invocation.source() instanceof Player player) || args.length > 1) {
                    return CompletableFuture.completedFuture(List.of());
                }
                String prefix = args.length == 0 ? "" : args[0];
                return context.supply(() -> context.visibleNames(player, context.friendsOf(player), prefix, false))
                        .exceptionally(error -> List.of());
            }
        };
    }

    public static SimpleCommand reply(SocialContext context, MessageService messages) {
        return invocation -> {
            Player player = player(context, invocation.source());
            if (player == null) {
                return;
            }
            if (invocation.arguments().length == 0) {
                context.send(player, "msg.reply-usage", Placeholder.unparsed("command", "/" + invocation.alias()));
                return;
            }
            messages.reply(player, String.join(" ", invocation.arguments()));
        };
    }

    public static SimpleCommand messageToggle(SocialContext context, FriendService friends) {
        return invocation -> {
            Player player = player(context, invocation.source());
            if (player == null) {
                return;
            }
            context.async(() -> {
                String current = context.setting(context.store().cached(player.getUniqueId()), SocialSetting.MESSAGES);
                friends.change(player, SocialSetting.MESSAGES, SocialSetting.MESSAGES.next(current));
            });
        };
    }

    public static SimpleCommand socialSpy(SocialContext context, MessageService messages) {
        return invocation -> {
            Player player = player(context, invocation.source());
            if (player != null) {
                messages.toggleSpy(player);
            }
        };
    }

    private static void settings(SocialContext context, FriendService friends, Player player, String[] args,
                                 String label) {
        if (args.length == 1) {
            friends.showSettings(player, label);
            return;
        }
        Optional<SocialSetting> setting = SocialSetting.byId(args[1]).filter(found -> context.enabled(found.feature()));
        if (setting.isEmpty() || args.length != 3) {
            context.send(player, "friends.usage-settings", Placeholder.unparsed("command", label));
            return;
        }
        Optional<String> value = setting.get().normalize(args[2]);
        if (value.isEmpty()) {
            context.send(player, "friends.invalid-value",
                    Placeholder.unparsed("value", args[2]),
                    Placeholder.unparsed("options", String.join(", ", setting.get().options())));
            return;
        }
        friends.change(player, setting.get(), value.get());
    }

    private static void partySettings(SocialContext context, PartyService party, FriendService friends, Player player,
                                      String[] args, String label) {
        if (args.length == 1) {
            party.showSettings(player).thenRun(() -> friends.showSettings(player, label));
            return;
        }
        Optional<PartyOption> option = PartyOption.byId(args[1])
                .filter(found -> context.settings().party().options().contains(found));
        if (option.isEmpty()) {
            settings(context, friends, player, args, label);
            return;
        }
        if (args.length != 3) {
            context.send(player, "party.usage-settings", Placeholder.unparsed("command", label));
            return;
        }
        Optional<String> value = option.get().normalize(args[2]);
        if (value.isEmpty()) {
            context.send(player, "friends.invalid-value",
                    Placeholder.unparsed("value", args[2]),
                    Placeholder.unparsed("options", String.join(", ", option.get().options())));
            return;
        }
        party.configure(player, Map.of(option.get(), value.get()));
    }

    private static List<String> partySettingSuggestions(SocialContext context, String[] args) {
        List<PartyOption> options = context.partyEnabled() ? context.settings().party().options() : List.of();
        if (args.length == 2) {
            List<String> keys = new ArrayList<>();
            options.forEach(option -> keys.add(option.id()));
            keys.addAll(settingSuggestions(context, args));
            return FriendService.filter(keys, args[1]);
        }
        if (args.length == 3) {
            Optional<PartyOption> option = PartyOption.byId(args[1]).filter(options::contains);
            if (option.isPresent()) {
                return FriendService.filter(option.get().options(), args[2]);
            }
        }
        return settingSuggestions(context, args);
    }

    private static List<String> joinSuggestions(SocialContext context, PartyService party, Player player,
                                                String prefix) {
        Set<String> names = new LinkedHashSet<>(party.inviterNames(player, prefix));
        names.addAll(context.visibleNames(player, context.friendsOf(player), prefix, false));
        return List.copyOf(names);
    }

    private static List<String> settingSuggestions(SocialContext context, String[] args) {
        if (args.length == 2) {
            List<String> keys = new ArrayList<>();
            for (SocialSetting setting : SocialSetting.values()) {
                if (context.enabled(setting.feature())) {
                    keys.add(setting.id());
                }
            }
            return FriendService.filter(keys, args[1]);
        }
        if (args.length == 3) {
            return SocialSetting.byId(args[1]).map(setting -> FriendService.filter(setting.options(), args[2]))
                    .orElse(List.of());
        }
        return List.of();
    }

    private static void named(SocialContext context, Player player, String[] args, String usage, String label,
                              Consumer<String> action) {
        if (args.length != 2) {
            context.send(player, usage, Placeholder.unparsed("command", label));
            return;
        }
        action.accept(args[1]);
    }

    private static void help(SocialContext context, Player player, String block, String label, List<String> actions) {
        context.send(player, block + ".help-header");
        for (String action : actions) {
            context.send(player, block + ".help." + action, Placeholder.unparsed("command", label));
        }
    }

    private static int page(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw) - 1);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Player player(SocialContext context, CommandSource source) {
        if (source instanceof Player player) {
            return player;
        }
        context.send(source, "general.players-only");
        return null;
    }
}
