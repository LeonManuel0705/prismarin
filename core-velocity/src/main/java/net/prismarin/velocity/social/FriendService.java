package net.prismarin.velocity.social;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.shared.text.TimeAgo;

public final class FriendService {

    public static final String JUMP_BYPASS = "prismarin.friends.jump.bypass";

    private static final int LIST_PAGE_SIZE = 10;

    public record Entry(UUID uuid, String name, boolean online, String server, long lastSeen, boolean jump) {
    }

    public record Request(UUID uuid, String name, long at, long expires) {
    }

    private final SocialContext context;
    private final Map<UUID, Long> lastRequest = new ConcurrentHashMap<>();

    public FriendService(SocialContext context) {
        this.context = context;
    }

    public String command() {
        return "/" + context.settings().friends().commands().getFirst();
    }

    public CompletableFuture<Void> add(Player sender, String input) {
        return context.async(() -> {
            SocialProfile own = context.store().cached(sender.getUniqueId());
            Optional<Player> online = context.findVisible(sender, own.friendIds(), input);
            if (online.isPresent()) {
                if (context.masked(sender, own.friendIds(), online.get())) {
                    notFound(sender, input);
                    return;
                }
                request(sender, online.get().getUniqueId(), online.get().getUsername());
                return;
            }
            if (context.nickInUse(input) || !validName(input)) {
                notFound(sender, input);
                return;
            }
            Optional<UUID> found;
            try {
                found = context.services().profiles().lookup(input).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                found = Optional.empty();
            }
            if (found.isEmpty()) {
                notFound(sender, input);
                return;
            }
            request(sender, found.get(), context.nameOf(found.get()));
        });
    }

    public CompletableFuture<Void> add(Player sender, UUID target) {
        return context.async(() -> {
            Optional<Player> online = context.proxy().getPlayer(target);
            SocialProfile own = context.store().cached(sender.getUniqueId());
            if (online.isEmpty() || !context.visibleTo(sender, online.get())
                    || context.masked(sender, own.friendIds(), online.get())) {
                notFound(sender, online.map(Player::getUsername).orElse("?"));
                return;
            }
            request(sender, target, online.get().getUsername());
        });
    }

    private void request(Player sender, UUID target, String targetName) {
        long now = System.currentTimeMillis();
        long cooldown = context.settings().friends().requestCooldownSeconds() * 1000L;
        Long last = lastRequest.get(sender.getUniqueId());
        if (last != null && now - last < cooldown) {
            context.send(sender, "friends.cooldown",
                    Placeholder.unparsed("seconds", String.valueOf((cooldown - (now - last)) / 1000L + 1L)));
            return;
        }
        long expiry = context.settings().friends().requestExpiryMillis();
        FriendRules.RequestOutcome outcome;
        synchronized (context.store().lock()) {
            SocialProfile mine = context.store().load(sender.getUniqueId()).pruned(now, expiry);
            SocialProfile theirs = context.store().load(target).pruned(now, expiry);
            outcome = FriendRules.request(mine, theirs, context.friendLimit(sender.getUniqueId()),
                    context.friendLimit(target), context.setting(theirs, SocialSetting.REQUESTS), now, expiry);
            switch (outcome) {
                case ACCEPTED -> {
                    context.store().save(mine.withFriend(target, now));
                    context.store().save(theirs.withFriend(sender.getUniqueId(), now));
                }
                case SENT -> {
                    context.store().save(mine.withOutgoing(target, now));
                    context.store().save(theirs.withIncoming(sender.getUniqueId(), now));
                }
                default -> {
                }
            }
        }
        switch (outcome) {
            case SELF -> context.send(sender, "friends.self");
            case ALREADY_FRIENDS -> context.send(sender, "friends.already-friends",
                    Placeholder.unparsed("player", targetName));
            case ALREADY_SENT -> context.send(sender, "friends.already-sent", Placeholder.unparsed("player", targetName));
            case NOT_ACCEPTING -> context.send(sender, "friends.not-accepting",
                    Placeholder.unparsed("player", targetName));
            case SENDER_FULL -> context.send(sender, "friends.limit-reached",
                    Placeholder.unparsed("limit", String.valueOf(context.friendLimit(sender.getUniqueId()))));
            case TARGET_FULL -> context.send(sender, "friends.target-full", Placeholder.unparsed("player", targetName));
            case ACCEPTED -> {
                context.send(sender, "friends.accepted", Placeholder.unparsed("player", targetName));
                context.sendTo(target, "friends.accepted", Placeholder.unparsed("player", sender.getUsername()));
            }
            case SENT -> {
                lastRequest.put(sender.getUniqueId(), now);
                if (expiry > 0) {
                    context.send(sender, "friends.request-sent", Placeholder.unparsed("player", targetName),
                            Placeholder.component("time", context.time(sender, TimeAgo.remaining(expiry))));
                } else {
                    context.send(sender, "friends.request-sent-open", Placeholder.unparsed("player", targetName));
                }
                context.proxy().getPlayer(target).ifPresent(receiver -> notifyRequest(receiver, sender.getUsername()));
            }
        }
    }

    private void notifyRequest(Player receiver, String senderName) {
        Component line = context.render(receiver, "friends.request-received", Placeholder.unparsed("player", senderName))
                .append(Component.space())
                .append(context.button(receiver, "friends.button.accept", command() + " accept " + senderName))
                .append(Component.space())
                .append(context.button(receiver, "friends.button.deny", command() + " deny " + senderName));
        receiver.sendMessage(line);
    }

    public CompletableFuture<Void> accept(Player player, String input) {
        return context.async(() -> incomingMatch(player, input).ifPresentOrElse(
                uuid -> acceptNow(player, uuid),
                () -> context.send(player, "friends.no-request", Placeholder.unparsed("player", input))));
    }

    public CompletableFuture<Void> accept(Player player, UUID other) {
        return context.async(() -> acceptNow(player, other));
    }

    private void acceptNow(Player player, UUID other) {
        long now = System.currentTimeMillis();
        long expiry = context.settings().friends().requestExpiryMillis();
        FriendRules.AcceptOutcome outcome;
        synchronized (context.store().lock()) {
            SocialProfile mine = context.store().load(player.getUniqueId()).pruned(now, expiry);
            SocialProfile theirs = context.store().load(other).pruned(now, expiry);
            outcome = FriendRules.accept(mine, theirs, context.friendLimit(player.getUniqueId()),
                    context.friendLimit(other), now, expiry);
            if (outcome == FriendRules.AcceptOutcome.ACCEPTED) {
                context.store().save(mine.withFriend(other, now));
                context.store().save(theirs.withFriend(player.getUniqueId(), now));
            }
        }
        String name = context.nameOf(other);
        switch (outcome) {
            case NO_REQUEST -> context.send(player, "friends.no-request", Placeholder.unparsed("player", name));
            case ALREADY_FRIENDS -> context.send(player, "friends.already-friends", Placeholder.unparsed("player", name));
            case SELF_FULL -> context.send(player, "friends.limit-reached",
                    Placeholder.unparsed("limit", String.valueOf(context.friendLimit(player.getUniqueId()))));
            case OTHER_FULL -> context.send(player, "friends.target-full", Placeholder.unparsed("player", name));
            case ACCEPTED -> {
                context.send(player, "friends.accepted", Placeholder.unparsed("player", name));
                context.sendTo(other, "friends.accepted", Placeholder.unparsed("player", player.getUsername()));
            }
        }
    }

    public CompletableFuture<Void> deny(Player player, String input) {
        return context.async(() -> incomingMatch(player, input).ifPresentOrElse(
                uuid -> denyNow(player, uuid),
                () -> context.send(player, "friends.no-request", Placeholder.unparsed("player", input))));
    }

    public CompletableFuture<Void> deny(Player player, UUID other) {
        return context.async(() -> denyNow(player, other));
    }

    private void denyNow(Player player, UUID other) {
        boolean had;
        synchronized (context.store().lock()) {
            SocialProfile mine = context.store().load(player.getUniqueId());
            had = mine.incoming().containsKey(other);
            if (had) {
                context.store().save(mine.withoutIncoming(other));
                SocialProfile theirs = context.store().load(other);
                context.store().save(theirs.withoutOutgoing(player.getUniqueId()));
            }
        }
        context.send(player, had ? "friends.denied" : "friends.no-request",
                Placeholder.unparsed("player", context.nameOf(other)));
    }

    public CompletableFuture<Void> cancel(Player player, String input) {
        return context.async(() -> {
            SocialProfile mine = context.store().cached(player.getUniqueId());
            Optional<UUID> match = match(mine.outgoing().keySet().stream().toList(), input);
            if (match.isEmpty()) {
                context.send(player, "friends.no-outgoing", Placeholder.unparsed("player", input));
                return;
            }
            cancelNow(player, match.get());
        });
    }

    public CompletableFuture<Void> cancel(Player player, UUID other) {
        return context.async(() -> cancelNow(player, other));
    }

    private void cancelNow(Player player, UUID other) {
        boolean had;
        synchronized (context.store().lock()) {
            SocialProfile mine = context.store().load(player.getUniqueId());
            had = mine.outgoing().containsKey(other);
            if (had) {
                context.store().save(mine.withoutOutgoing(other));
                context.store().save(context.store().load(other).withoutIncoming(player.getUniqueId()));
            }
        }
        context.send(player, had ? "friends.cancelled" : "friends.no-outgoing",
                Placeholder.unparsed("player", context.nameOf(other)));
    }

    public CompletableFuture<Void> remove(Player player, String input) {
        return context.async(() -> friendMatch(player, input).ifPresentOrElse(
                uuid -> removeNow(player, uuid),
                () -> context.send(player, "friends.not-friends", Placeholder.unparsed("player", input))));
    }

    public CompletableFuture<Void> remove(Player player, UUID other) {
        return context.async(() -> removeNow(player, other));
    }

    private void removeNow(Player player, UUID other) {
        boolean had;
        synchronized (context.store().lock()) {
            SocialProfile mine = context.store().load(player.getUniqueId());
            had = mine.friendsWith(other);
            if (had) {
                context.store().save(mine.withoutFriend(other));
                context.store().save(context.store().load(other).withoutFriend(player.getUniqueId()));
            }
        }
        context.send(player, had ? "friends.removed" : "friends.not-friends",
                Placeholder.unparsed("player", context.nameOf(other)));
    }

    public CompletableFuture<Void> jump(Player player, String input) {
        return context.async(() -> friendMatch(player, input).ifPresentOrElse(
                uuid -> jumpNow(player, uuid),
                () -> context.send(player, "friends.not-friends", Placeholder.unparsed("player", input))));
    }

    public CompletableFuture<Void> jump(Player player, UUID other) {
        return context.async(() -> jumpNow(player, other));
    }

    private void jumpNow(Player player, UUID other) {
        String name = context.nameOf(other);
        if (!context.store().cached(player.getUniqueId()).friendsWith(other)) {
            context.send(player, "friends.not-friends", Placeholder.unparsed("player", name));
            return;
        }
        Optional<Player> target = context.proxy().getPlayer(other).filter(found -> context.visibleTo(player, found));
        Optional<ServerConnection> server = target.flatMap(Player::getCurrentServer);
        if (server.isEmpty()) {
            context.send(player, "friends.jump-offline", Placeholder.unparsed("player", name));
            return;
        }
        if (SocialSetting.OFF.equals(context.setting(context.store().cached(other), SocialSetting.JUMP))
                && !player.hasPermission(JUMP_BYPASS)) {
            context.send(player, "friends.jump-denied", Placeholder.unparsed("player", name));
            return;
        }
        String serverName = server.get().getServerInfo().getName();
        if (player.getCurrentServer().map(own -> own.getServerInfo().getName().equals(serverName)).orElse(false)) {
            context.send(player, "friends.jump-same", Placeholder.unparsed("server", serverName));
            return;
        }
        context.send(player, "friends.jumping", Placeholder.unparsed("player", name),
                Placeholder.unparsed("server", serverName));
        player.createConnectionRequest(server.get().getServer()).connect().thenAccept(result -> {
            if (!result.isSuccessful()) {
                context.send(player, "friends.jump-failed", Placeholder.unparsed("server", serverName));
            }
        }).exceptionally(error -> {
            context.send(player, "friends.jump-failed", Placeholder.unparsed("server", serverName));
            return null;
        });
    }

    public CompletableFuture<Void> list(Player player, int requestedPage) {
        return context.async(() -> {
            List<Entry> entries = entries(player);
            if (entries.isEmpty()) {
                context.send(player, "friends.list-empty", Placeholder.unparsed("command", command()));
                return;
            }
            Page<Entry> page = Page.of(entries, requestedPage, LIST_PAGE_SIZE);
            long online = entries.stream().filter(Entry::online).count();
            context.send(player, "friends.list-header",
                    Placeholder.unparsed("online", String.valueOf(online)),
                    Placeholder.unparsed("total", String.valueOf(entries.size())),
                    Placeholder.unparsed("limit", String.valueOf(context.friendLimit(player.getUniqueId()))),
                    Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                    Placeholder.unparsed("pages", String.valueOf(page.pages())));
            long now = System.currentTimeMillis();
            for (Entry entry : page.entries()) {
                if (entry.online()) {
                    context.send(player, "friends.list-online", Placeholder.unparsed("player", entry.name()),
                            Placeholder.unparsed("server", entry.server()));
                } else {
                    context.send(player, "friends.list-offline", Placeholder.unparsed("player", entry.name()),
                            Placeholder.component("time", context.time(player, TimeAgo.since(entry.lastSeen(), now))));
                }
            }
            if (page.pages() > 1) {
                Component navigation = Component.empty();
                if (page.page() > 0) {
                    navigation = navigation.append(context.button(player, "friends.button.previous",
                            command() + " list " + page.page())).append(Component.space());
                }
                if (page.page() + 1 < page.pages()) {
                    navigation = navigation.append(context.button(player, "friends.button.next",
                            command() + " list " + (page.page() + 2)));
                }
                player.sendMessage(navigation);
            }
        });
    }

    public List<Entry> entries(Player viewer) {
        SocialProfile mine = context.store().cached(viewer.getUniqueId());
        List<Entry> entries = new ArrayList<>();
        for (UUID friend : mine.friendIds()) {
            Optional<Player> online = context.proxy().getPlayer(friend).filter(found -> context.visibleTo(viewer, found));
            String name = online.map(Player::getUsername).orElseGet(() -> context.nameOf(friend));
            boolean jump = online.isPresent() && (!SocialSetting.OFF.equals(
                    context.setting(context.store().cached(friend), SocialSetting.JUMP))
                    || viewer.hasPermission(JUMP_BYPASS));
            entries.add(new Entry(friend, name, online.isPresent(),
                    online.map(context::serverOf).orElse(""),
                    online.isPresent() ? System.currentTimeMillis() : context.lastSeen(friend), jump));
        }
        entries.sort(Comparator.comparing((Entry entry) -> !entry.online())
                .thenComparing(entry -> entry.name().toLowerCase(Locale.ROOT)));
        return entries;
    }

    public List<Request> incoming(Player viewer) {
        long now = System.currentTimeMillis();
        long expiry = context.settings().friends().requestExpiryMillis();
        SocialProfile mine = context.store().cached(viewer.getUniqueId()).pruned(now, expiry);
        return requests(mine.incoming(), expiry);
    }

    public List<Request> outgoing(Player viewer) {
        long now = System.currentTimeMillis();
        long expiry = context.settings().friends().requestExpiryMillis();
        SocialProfile mine = context.store().cached(viewer.getUniqueId()).pruned(now, expiry);
        return requests(mine.outgoing(), expiry);
    }

    private List<Request> requests(Map<UUID, Long> source, long expiry) {
        List<Request> requests = new ArrayList<>();
        source.forEach((uuid, at) -> requests.add(new Request(uuid, context.nameOf(uuid), at,
                expiry > 0 ? at + expiry : 0L)));
        requests.sort(Comparator.comparingLong(Request::at).reversed());
        return requests;
    }

    public CompletableFuture<Void> showRequests(Player player) {
        return context.async(() -> {
            List<Request> incoming = incoming(player);
            List<Request> outgoing = outgoing(player);
            if (incoming.isEmpty() && outgoing.isEmpty()) {
                context.send(player, "friends.requests-empty");
                return;
            }
            long now = System.currentTimeMillis();
            if (!incoming.isEmpty()) {
                context.send(player, "friends.requests-header",
                        Placeholder.unparsed("count", String.valueOf(incoming.size())));
                for (Request request : incoming) {
                    player.sendMessage(context.render(player, "friends.requests-entry",
                                    Placeholder.unparsed("player", request.name()))
                            .append(Component.space())
                            .append(context.button(player, "friends.button.accept",
                                    command() + " accept " + request.name()))
                            .append(Component.space())
                            .append(context.button(player, "friends.button.deny",
                                    command() + " deny " + request.name())));
                }
            }
            if (!outgoing.isEmpty()) {
                context.send(player, "friends.outgoing-header",
                        Placeholder.unparsed("count", String.valueOf(outgoing.size())));
                for (Request request : outgoing) {
                    Component entry = request.expires() > 0
                            ? context.render(player, "friends.outgoing-entry",
                                    Placeholder.unparsed("player", request.name()),
                                    Placeholder.component("time",
                                            context.time(player, TimeAgo.remaining(request.expires() - now))))
                            : context.render(player, "friends.outgoing-entry-open",
                                    Placeholder.unparsed("player", request.name()));
                    player.sendMessage(entry
                            .append(Component.space())
                            .append(context.button(player, "friends.button.cancel",
                                    command() + " cancel " + request.name())));
                }
            }
        });
    }

    public CompletableFuture<Void> showSettings(Player player, String command) {
        return context.async(() -> {
            SocialProfile mine = context.store().cached(player.getUniqueId());
            context.send(player, "friends.settings-header");
            for (SocialSetting setting : SocialSetting.values()) {
                if (!context.enabled(setting.feature())) {
                    continue;
                }
                String value = context.setting(mine, setting);
                player.sendMessage(context.render(player, "friends.settings-entry",
                                Placeholder.component("setting", context.render(player, "friends.setting." + setting.id())),
                                Placeholder.component("value", context.render(player, "friends.value." + value)))
                        .append(Component.space())
                        .append(context.button(player, "friends.button.change",
                                command + " settings " + setting.id() + " " + setting.next(value))));
            }
        });
    }

    public CompletableFuture<Void> change(Player player, SocialSetting setting, String value) {
        return context.async(() -> {
            synchronized (context.store().lock()) {
                context.store().save(context.store().load(player.getUniqueId()).withSetting(setting, value));
            }
            context.send(player, "friends.setting-changed",
                    Placeholder.component("setting", context.render(player, "friends.setting." + setting.id())),
                    Placeholder.component("value", context.render(player, "friends.value." + value)));
        });
    }

    public List<String> incomingNames(Player player, String prefix) {
        return filter(incoming(player).stream().map(Request::name).toList(), prefix);
    }

    public List<String> outgoingNames(Player player, String prefix) {
        return filter(outgoing(player).stream().map(Request::name).toList(), prefix);
    }

    public List<String> friendNames(Player player, String prefix, boolean onlineOnly) {
        return filter(entries(player).stream().filter(entry -> !onlineOnly || entry.online()).map(Entry::name)
                .toList(), prefix);
    }

    private Optional<UUID> incomingMatch(Player player, String input) {
        long now = System.currentTimeMillis();
        long expiry = context.settings().friends().requestExpiryMillis();
        SocialProfile mine = context.store().cached(player.getUniqueId()).pruned(now, expiry);
        return match(mine.incoming().keySet().stream().toList(), input);
    }

    private Optional<UUID> friendMatch(Player player, String input) {
        return match(context.store().cached(player.getUniqueId()).friendIds().stream().toList(), input);
    }

    private Optional<UUID> match(List<UUID> candidates, String input) {
        for (UUID candidate : candidates) {
            if (context.nameOf(candidate).equalsIgnoreCase(input)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private void notFound(Player sender, String input) {
        context.send(sender, "friends.not-found", Placeholder.unparsed("player", input));
    }

    static boolean validName(String input) {
        return input != null && input.matches("[A-Za-z0-9_]{1,16}");
    }

    static List<String> filter(List<String> names, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower))
                .sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
