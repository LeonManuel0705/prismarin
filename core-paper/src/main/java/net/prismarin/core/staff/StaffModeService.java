package net.prismarin.core.staff;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Locale;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.staff.StaffModeChangedEvent;
import net.prismarin.core.CoreModules;
import net.prismarin.core.ModuleState;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.vanish.VanishService;
import net.prismarin.shared.platform.CoreServices;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

@Singleton
public final class StaffModeService implements Listener {

    public static final String PERMISSION = "prismarin.staff.mode";
    public static final String SETTING = "staff-mode";
    public static final String AUTO_BEFORE = "staff-mode.auto-nick-before";
    private static final long JOIN_DELAY_TICKS = 30L;

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final LocaleService locale;
    private final VanishService vanish;
    private final NickService nicks;
    private final ModuleState modules;
    private final StaffModeSettings settings;
    private final EventBus events;

    @Inject
    public StaffModeService(PrismarinCorePlugin plugin, ProfileService profiles, LocaleService locale,
                            VanishService vanish, NickService nicks, ModuleState modules, CoreServices services) {
        this.events = services.events();
        this.plugin = plugin;
        this.profiles = profiles;
        this.locale = locale;
        this.vanish = vanish;
        this.nicks = nicks;
        this.modules = modules;
        this.settings = StaffModeSettings.load(plugin.getDataFolder().toPath().resolve("config.yml"));
    }

    public boolean active(Player player) {
        return profiles.getCached(player.getUniqueId())
                .map(profile -> Boolean.parseBoolean(profile.getSettings().get(SETTING)))
                .orElse(false);
    }

    public void toggle(Player player) {
        if (active(player)) {
            leave(player);
        } else {
            enter(player);
        }
    }

    private boolean nickEnabled() {
        return settings.nick() && modules.enabled(CoreModules.NICK);
    }

    private boolean vanishEnabled() {
        return settings.vanish() && modules.enabled(CoreModules.VANISH);
    }

    private void enter(Player player) {
        boolean autoBefore = nickEnabled() && nicks.autoNick(player.getUniqueId());
        profiles.modify(player.getUniqueId(), profile -> {
            profile.getSettings().put(SETTING, "true");
            profile.getSettings().put(AUTO_BEFORE, String.valueOf(autoBefore));
        });
        if (vanishEnabled() && !vanish.vanished(player)) {
            vanish.vanish(player);
        }
        if (nickEnabled()) {
            nicks.setAutoNick(player.getUniqueId(), true);
            if (!nicks.isNicked(player.getUniqueId())) {
                nicks.nickRandom(player.getUniqueId());
            }
        }
        send(player, "staff-mode.enabled", Placeholder.unparsed("parts", parts(player)));
        events.publish(new StaffModeChangedEvent(player.getUniqueId(), true, nickEnabled(), vanishEnabled()));
    }

    private void leave(Player player) {
        boolean autoBefore = profiles.getCached(player.getUniqueId())
                .map(profile -> Boolean.parseBoolean(profile.getSettings().get(AUTO_BEFORE)))
                .orElse(false);
        profiles.modify(player.getUniqueId(), profile -> {
            profile.getSettings().remove(SETTING);
            profile.getSettings().remove(AUTO_BEFORE);
        });
        if (vanishEnabled() && vanish.vanished(player)) {
            vanish.reveal(player);
        }
        if (nickEnabled()) {
            nicks.setAutoNick(player.getUniqueId(), autoBefore);
            if (!autoBefore && nicks.isNicked(player.getUniqueId())) {
                nicks.unnick(player.getUniqueId());
            }
        }
        send(player, "staff-mode.disabled");
        events.publish(new StaffModeChangedEvent(player.getUniqueId(), false, false, false));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !active(player)) {
                return;
            }
            if (!player.hasPermission(PERMISSION)) {
                leave(player);
                return;
            }
            if (vanishEnabled() && !vanish.vanished(player)) {
                vanish.vanish(player);
            }
            if (nickEnabled() && !nicks.isNicked(player.getUniqueId()) && nicks.nicksShownHere()) {
                nicks.nickRandom(player.getUniqueId());
            }
            player.sendActionBar(locale.render("staff-mode.actionbar", PlayerLocales.of(player, profiles)));
        }, JOIN_DELAY_TICKS);
    }

    private String parts(Player player) {
        Locale target = PlayerLocales.of(player, profiles);
        StringBuilder text = new StringBuilder();
        if (nickEnabled()) {
            text.append(locale.raw("staff-mode.part-nick", target));
        }
        if (vanishEnabled()) {
            if (!text.isEmpty()) {
                text.append(locale.raw("staff-mode.part-and", target));
            }
            text.append(locale.raw("staff-mode.part-vanish", target));
        }
        return text.isEmpty() ? locale.raw("staff-mode.part-none", target) : text.toString();
    }

    private void send(Player player, String key, TagResolver... resolvers) {
        player.sendMessage(locale.render(key, PlayerLocales.of(player, profiles), resolvers));
    }
}
