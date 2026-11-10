package net.prismarin.velocity.social;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.text.TimeAgo;
import net.prismarin.velocity.admin.ModerationChannel;
import net.prismarin.velocity.admin.NickDirectory;
import net.prismarin.velocity.admin.VanishRegistry;
import net.prismarin.velocity.locale.ProxyMessages;
import org.slf4j.Logger;

public final class SocialContext {

    public static final String REAL_NAMES = "prismarin.nick.realname";
    public static final String SPY = "prismarin.msg.spy";
    public static final String MESSAGE_BYPASS = "prismarin.msg.bypass";

    private final Object plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final CoreServices services;
    private final ProxyMessages messages;
    private final SocialSettings settings;
    private final SocialStore store;
    private final VanishRegistry vanish;
    private final NickDirectory nicks;
    private final ModerationChannel channel;
    private final Executor executor;
    private final boolean friends;
    private final boolean party;
    private final boolean privateMessages;
    private final Set<UUID> announced = ConcurrentHashMap.newKeySet();
    private final Set<UUID> spies = ConcurrentHashMap.newKeySet();

    public SocialContext(Object plugin, ProxyServer proxy, Logger logger, CoreServices services,
                         ProxyMessages messages, SocialSettings settings, SocialStore store, VanishRegistry vanish,
                         NickDirectory nicks, ModerationChannel channel, Executor executor, boolean friends,
                         boolean party, boolean privateMessages) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.services = services;
        this.messages = messages;
        this.settings = settings;
        this.store = store;
        this.vanish = vanish;
        this.nicks = nicks;
        this.channel = channel;
        this.executor = executor;
        this.friends = friends;
        this.party = party;
        this.privateMessages = privateMessages;
    }

    public Object plugin() {
        return plugin;
    }

    public ProxyServer proxy() {
        return proxy;
    }

    public CoreServices services() {
        return services;
    }

    public ProxyMessages messages() {
        return messages;
    }

    public SocialSettings settings() {
        return settings;
    }

    public SocialStore store() {
        return store;
    }

    public VanishRegistry vanish() {
        return vanish;
    }

    public ModerationChannel channel() {
        return channel;
    }

    public boolean friendsEnabled() {
        return friends;
    }

    public boolean partyEnabled() {
        return party;
    }

    public boolean messagesEnabled() {
        return privateMessages;
    }

    public boolean enabled(SocialSetting.Feature feature) {
        return switch (feature) {
            case FRIENDS -> friends;
            case PARTY -> party;
            case MESSAGES -> privateMessages;
        };
    }

    public Set<UUID> spies() {
        return spies;
    }

    public void send(CommandSource source, String key, TagResolver... resolvers) {
        messages.send(source, key, resolvers);
    }

    public Component render(CommandSource source, String key, TagResolver... resolvers) {
        return messages.render(source, key, resolvers);
    }

    public Component button(CommandSource viewer, String key, String command) {
        return render(viewer, key)
                .clickEvent(ClickEvent.runCommand(command))
                .hoverEvent(HoverEvent.showText(render(viewer, key + "-hover")));
    }

    public Component time(CommandSource viewer, TimeAgo time) {
        return render(viewer, "friends.time." + time.unit().key(),
                Placeholder.unparsed("amount", String.valueOf(time.amount())));
    }

    public void sendTo(UUID player, String key, TagResolver... resolvers) {
        proxy.getPlayer(player).ifPresent(online -> messages.send(online, key, resolvers));
    }

    public CompletableFuture<Void> async(Runnable task) {
        return CompletableFuture.runAsync(task, executor).exceptionally(error -> {
            logger.warn("Social task failed", error);
            return null;
        });
    }

    public <T> CompletableFuture<T> supply(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    public void later(Runnable task, long delay, TimeUnit unit) {
        proxy.getScheduler().buildTask(plugin, task).delay(delay, unit).schedule();
    }

    public void announce(UUID player) {
        announced.add(player);
    }

    public boolean unannounce(UUID player) {
        return announced.remove(player);
    }

    public boolean announced(UUID player) {
        return announced.contains(player);
    }

    public boolean publiclyOnline(Player player) {
        return player.isActive() && announced.contains(player.getUniqueId())
                && !vanish.vanished(player.getUniqueId());
    }

    public boolean visibleTo(CommandSource viewer, Player target) {
        if (viewer instanceof Player self && self.getUniqueId().equals(target.getUniqueId())) {
            return true;
        }
        return target.isActive() && announced.contains(target.getUniqueId()) && vanish.canSee(viewer, target);
    }

    public boolean privileged(CommandSource viewer) {
        return !(viewer instanceof Player) || viewer.hasPermission(REAL_NAMES);
    }

    public String visibleName(CommandSource viewer, Set<UUID> viewerFriends, Player target) {
        return visibleName(viewer, viewerFriends, target.getUniqueId(), target.getUsername());
    }

    public String visibleName(CommandSource viewer, Set<UUID> viewerFriends, UUID subject, String realName) {
        boolean self = viewer instanceof Player player && player.getUniqueId().equals(subject);
        return NameRules.visible(self, viewerFriends.contains(subject), privileged(viewer), realName,
                nicks.shownNick(subject));
    }

    public String shownTo(Player viewer, UUID subject) {
        return visibleName(viewer, friendsOf(viewer), subject, nameOf(subject));
    }

    public boolean masked(CommandSource viewer, Set<UUID> viewerFriends, Player target) {
        return masked(viewer, viewerFriends, target.getUniqueId());
    }

    public boolean masked(CommandSource viewer, Set<UUID> viewerFriends, UUID subject) {
        boolean self = viewer instanceof Player player && player.getUniqueId().equals(subject);
        return NameRules.masked(self, viewerFriends.contains(subject), privileged(viewer), nicks.shownNick(subject));
    }

    public Optional<Player> findVisible(CommandSource viewer, Set<UUID> viewerFriends, String input) {
        for (Player candidate : proxy.getAllPlayers()) {
            if (visibleTo(viewer, candidate)
                    && visibleName(viewer, viewerFriends, candidate).equalsIgnoreCase(input)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    public boolean nickInUse(String input) {
        return nicks.ownerOf(input).flatMap(proxy::getPlayer).isPresent();
    }

    public List<String> visibleNames(CommandSource viewer, Set<UUID> viewerFriends, String prefix,
                                     boolean includeSelf) {
        return visibleNames(viewer, viewerFriends, prefix, includeSelf, true);
    }

    public List<String> visibleNames(CommandSource viewer, Set<UUID> viewerFriends, String prefix,
                                     boolean includeSelf, boolean includeMasked) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> names = new ArrayList<>();
        for (Player candidate : proxy.getAllPlayers()) {
            boolean self = viewer instanceof Player player && player.getUniqueId().equals(candidate.getUniqueId());
            if ((self && !includeSelf) || !visibleTo(viewer, candidate)
                    || !includeMasked && masked(viewer, viewerFriends, candidate)) {
                continue;
            }
            String name = visibleName(viewer, viewerFriends, candidate);
            if (name.toLowerCase(Locale.ROOT).startsWith(lower)) {
                names.add(name);
            }
        }
        names.sort(String.CASE_INSENSITIVE_ORDER);
        return names;
    }

    public Set<UUID> friendsOf(CommandSource viewer) {
        if (!(viewer instanceof Player player)) {
            return Set.of();
        }
        return store.cached(player.getUniqueId()).friendIds();
    }

    public String nameOf(UUID uuid) {
        Optional<Player> online = proxy.getPlayer(uuid);
        if (online.isPresent()) {
            return online.get().getUsername();
        }
        Optional<SocialStore.Presence> presence = store.presence(uuid);
        if (presence.isPresent()) {
            return presence.get().name();
        }
        try {
            return services.profiles().find(uuid).get(5, TimeUnit.SECONDS)
                    .map(profile -> profile.getName()).orElse(uuid.toString().substring(0, 8));
        } catch (Exception e) {
            return uuid.toString().substring(0, 8);
        }
    }

    public long lastSeen(UUID uuid) {
        Optional<SocialStore.Presence> presence = store.presence(uuid);
        if (presence.isPresent() && presence.get().lastSeen() > 0) {
            return presence.get().lastSeen();
        }
        try {
            return services.profiles().find(uuid).get(5, TimeUnit.SECONDS)
                    .map(profile -> profile.getLastSeen() == null ? 0L : profile.getLastSeen().toEpochMilli())
                    .orElse(0L);
        } catch (Exception e) {
            return 0L;
        }
    }

    public String serverOf(Player player) {
        return player.getCurrentServer().map(connection -> connection.getServerInfo().getName()).orElse("-");
    }

    public String setting(SocialProfile profile, SocialSetting setting) {
        return profile.setting(setting, settings.defaultValue(setting));
    }

    public int friendLimit(UUID player) {
        int base = settings.friends().maxFriends();
        return proxy.getPlayer(player)
                .map(online -> FriendRules.limit(base, settings.friends().limitPermissions(), online::hasPermission))
                .orElse(Math.max(base, settings.friends().limitPermissions().values().stream()
                        .mapToInt(Integer::intValue).max().orElse(base)));
    }

    public int partyLimit(UUID leader) {
        int base = settings.party().maxSize();
        return proxy.getPlayer(leader)
                .map(online -> FriendRules.limit(base, settings.party().sizePermissions(), online::hasPermission))
                .orElse(base);
    }

    public CompletableFuture<Boolean> muted(UUID player) {
        return services.punishments().active(player, PunishmentType.MUTE)
                .thenApply(Optional::isPresent)
                .exceptionally(error -> false);
    }
}
