package net.prismarin.core.command;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.PlayerProfile;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.shared.config.CoreConfig;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

@Singleton
public final class LevelCommand implements TabExecutor {

    public static final String PERMISSION = "prismarin.admin.level";

    private final PrismarinCorePlugin plugin;
    private final ProfileService profiles;
    private final LocaleService locale;
    private final CoreConfig config;

    @Inject
    public LevelCommand(PrismarinCorePlugin plugin, ProfileService profiles, LocaleService locale, CoreConfig config) {
        this.plugin = plugin;
        this.profiles = profiles;
        this.locale = locale;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            send(sender, "general.no-permission");
            return true;
        }
        LevelCommandParser.Result result = LevelCommandParser.parse(args);
        if (result instanceof LevelCommandParser.Invalid invalid) {
            send(sender, invalid.messageKey());
            return true;
        }
        String targetName = result instanceof LevelCommandParser.Show show ? show.targetName()
                : ((LevelCommandParser.Change) result).targetName();
        resolve(targetName).thenAccept(target -> {
            if (target.isEmpty()) {
                send(sender, "general.player-not-found", Placeholder.unparsed("player", targetName));
                return;
            }
            if (result instanceof LevelCommandParser.Change change) {
                change(sender, target.get(), targetName, change);
            } else {
                show(sender, target.get(), targetName);
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            return List.of();
        }
        return switch (args.length) {
            case 1 -> filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[0]);
            case 2 -> filter(List.of("set", "add", "remove", "addxp"), args[1]);
            case 3 -> filter(List.of("1", "5", "10", "100"), args[2]);
            default -> List.of();
        };
    }

    private CompletableFuture<Optional<UUID>> resolve(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return CompletableFuture.completedFuture(Optional.of(online.getUniqueId()));
        }
        return profiles.lookup(name);
    }

    private void show(CommandSender sender, UUID target, String name) {
        Optional<PlayerProfile> cached = profiles.getCached(target);
        CompletableFuture<Optional<PlayerProfile>> profile = cached.isPresent()
                ? CompletableFuture.completedFuture(cached) : profiles.find(target);
        profile.thenAccept(found -> {
            if (found.isEmpty()) {
                send(sender, "general.player-not-found", Placeholder.unparsed("player", name));
                return;
            }
            PlayerProfile value = found.get();
            send(sender, "level.show", Placeholder.unparsed("player", value.getName()),
                    Placeholder.unparsed("level", String.valueOf(value.getLevel())),
                    Placeholder.unparsed("xp", String.valueOf(value.getXp())),
                    Placeholder.unparsed("max", String.valueOf(value.xpForNextLevel())));
        });
    }

    private void change(CommandSender sender, UUID target, String name, LevelCommandParser.Change change) {
        int[] before = new int[1];
        profiles.modify(target, profile -> {
            before[0] = profile.getLevel();
            int level = LevelCommandParser.apply(profile.getLevel(), change.action(), change.amount());
            switch (change.action()) {
                case ADD_XP -> profile.addXp(change.amount(), LevelService.REASON_COMMAND);
                case SET -> profile.setProgress(level, 0L, LevelService.REASON_COMMAND);
                case ADD, REMOVE -> profile.setLevel(level, LevelService.REASON_COMMAND);
            }
        }).whenComplete((profile, error) -> {
            if (error != null || profile == null) {
                send(sender, "general.player-not-found", Placeholder.unparsed("player", name));
                return;
            }
            send(sender, "level.changed", Placeholder.unparsed("player", profile.getName()),
                    Placeholder.unparsed("before", String.valueOf(before[0])),
                    Placeholder.unparsed("level", String.valueOf(profile.getLevel())),
                    Placeholder.unparsed("xp", String.valueOf(profile.getXp())));
            Player online = Bukkit.getPlayer(target);
            if (online != null && profile.getLevel() != before[0]) {
                Bukkit.getScheduler().runTask(plugin, () -> online.sendMessage(locale.render(
                        profile.getLevel() > before[0] ? "level.up" : "level.down",
                        PlayerLocales.of(online, profiles),
                        Placeholder.unparsed("level", String.valueOf(profile.getLevel())))));
            }
        });
    }

    private void send(CommandSender sender, String key, TagResolver... resolvers) {
        Locale target = sender instanceof Player player ? PlayerLocales.of(player, profiles)
                : Locale.forLanguageTag(config.defaultLocale());
        Component message = locale.render(key, target, resolvers);
        if (Bukkit.isPrimaryThread()) {
            sender.sendMessage(message);
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> sender.sendMessage(message));
        }
    }

    private static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
