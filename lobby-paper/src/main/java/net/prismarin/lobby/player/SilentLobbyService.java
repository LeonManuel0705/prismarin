package net.prismarin.lobby.player;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.LobbySettings;
import net.prismarin.lobby.item.LobbyItems;
import net.prismarin.lobby.util.CooldownTracker;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

@Singleton
public final class SilentLobbyService implements Listener {

    private static final Duration TOGGLE_COOLDOWN = Duration.ofSeconds(3);
    private static final int EFFECT_TICKS = 18;
    private static final int RING_POINTS = 28;
    private static final double HEIGHT = 2.2;
    private static final Sound ON_START = Sound.sound(Key.key("block.beacon.activate"), Sound.Source.MASTER,
            0.5f, 1.6f);
    private static final Sound ON_END = Sound.sound(Key.key("block.amethyst_block.chime"), Sound.Source.MASTER,
            0.9f, 1.5f);
    private static final Sound OFF_START = Sound.sound(Key.key("block.beacon.deactivate"), Sound.Source.MASTER,
            0.5f, 1.6f);
    private static final Sound OFF_END = Sound.sound(Key.key("block.amethyst_block.chime"), Sound.Source.MASTER,
            0.9f, 0.9f);

    private final LobbyPlugin plugin;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final LobbyItems items;
    private final LobbyVisibility visibility;
    private final LobbySettings settings;
    private final CooldownTracker cooldown = new CooldownTracker(TOGGLE_COOLDOWN);
    private final Map<UUID, BukkitTask> effects = new ConcurrentHashMap<>();

    @Inject
    public SilentLobbyService(LobbyPlugin plugin, LocaleService locale, ProfileService profiles, LobbyItems items,
                              LobbyVisibility visibility, LobbySettings settings) {
        this.plugin = plugin;
        this.locale = locale;
        this.profiles = profiles;
        this.items = items;
        this.visibility = visibility;
        this.settings = settings;
    }

    public boolean silent(Player player) {
        return visibility.silent(player.getUniqueId());
    }

    public void toggle(Player player) {
        Locale playerLocale = PlayerLocales.of(player, profiles);
        String permission = settings.item(LobbySettings.SILENT_ITEM).permission();
        if (!permission.isEmpty() && !player.hasPermission(permission)) {
            player.sendMessage(locale.render("general.no-permission", playerLocale));
            return;
        }
        long now = System.currentTimeMillis();
        if (!cooldown.tryUse(player.getUniqueId(), now)) {
            long seconds = (cooldown.remainingMillis(player.getUniqueId(), now) + 999L) / 1000L;
            player.sendActionBar(locale.render("lobby.silent.cooldown", playerLocale,
                    Placeholder.unparsed("seconds", String.valueOf(seconds))));
            return;
        }
        boolean on = !visibility.silent(player.getUniqueId());
        visibility.silent(player, on);
        items.refreshSilent(player, on);
        player.sendActionBar(locale.render(on ? "lobby.silent.on" : "lobby.silent.off", playerLocale));
        play(player, on);
    }

    public void end(Player player) {
        if (visibility.forgetSilent(player.getUniqueId())) {
            visibility.refresh(player);
        }
        stopEffect(player.getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        visibility.refresh(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        visibility.forgetSilent(uuid);
        cooldown.clear(uuid);
        stopEffect(uuid);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        UUID sender = event.getPlayer().getUniqueId();
        event.viewers().removeIf(viewer -> viewer instanceof Player player
                && !player.getUniqueId().equals(sender) && visibility.silent(player.getUniqueId()));
    }

    private void play(Player player, boolean on) {
        stopEffect(player.getUniqueId());
        player.playSound(on ? ON_START : OFF_START, Sound.Emitter.self());
        int[] tick = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || tick[0] > EFFECT_TICKS) {
                stopEffect(player.getUniqueId());
                return;
            }
            frame(player, tick[0], on);
            if (tick[0] == EFFECT_TICKS) {
                player.playSound(on ? ON_END : OFF_END, Sound.Emitter.self());
            }
            tick[0]++;
        }, 0L, 1L);
        effects.put(player.getUniqueId(), task);
    }

    private void frame(Player player, int tick, boolean on) {
        Location base = player.getLocation();
        double progress = (double) tick / EFFECT_TICKS;
        double eased = 1 - Math.pow(1 - progress, 3);
        double height = on ? eased * HEIGHT : (1 - eased) * HEIGHT;
        double radius = on ? 0.9 - 0.35 * eased : 0.55 + 0.35 * eased;
        for (int strand = 0; strand < 2; strand++) {
            double angle = tick * 0.55 + strand * Math.PI;
            for (int trail = 0; trail < 3; trail++) {
                double back = angle - trail * 0.18;
                double y = height - (on ? trail * 0.06 : -trail * 0.06);
                player.spawnParticle(Particle.END_ROD, base.getX() + Math.cos(back) * radius, base.getY() + y,
                        base.getZ() + Math.sin(back) * radius, 1, 0, 0, 0, 0);
            }
        }
        if (tick == EFFECT_TICKS) {
            double ringY = on ? base.getY() + HEIGHT : base.getY() + 0.1;
            for (int point = 0; point < RING_POINTS; point++) {
                double angle = Math.PI * 2 * point / RING_POINTS;
                player.spawnParticle(on ? Particle.REVERSE_PORTAL : Particle.PORTAL,
                        base.getX() + Math.cos(angle) * 0.75, ringY, base.getZ() + Math.sin(angle) * 0.75,
                        1, 0, 0, 0, on ? 0.02 : 0.4);
            }
        }
    }

    private void stopEffect(UUID player) {
        BukkitTask task = effects.remove(player);
        if (task != null) {
            task.cancel();
        }
    }
}
