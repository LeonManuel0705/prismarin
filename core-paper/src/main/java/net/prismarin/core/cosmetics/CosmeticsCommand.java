package net.prismarin.core.cosmetics;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.cosmetics.Cosmetic;
import net.prismarin.api.cosmetics.CosmeticResult;
import net.prismarin.api.cosmetics.CosmeticSlot;
import net.prismarin.api.cosmetics.CosmeticSource;
import net.prismarin.api.cosmetics.PlayerCosmetics;
import net.prismarin.api.player.NetworkPlayer;
import net.prismarin.api.player.PlayerService;
import net.prismarin.core.cosmetics.CosmeticMessages.Action;
import net.prismarin.shared.cosmetics.CosmeticCategory;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

@Singleton
public final class CosmeticsCommand implements TabExecutor {

    private final PaperCosmetics cosmetics;
    private final CosmeticMenus menus;
    private final CosmeticTexts texts;
    private final PlayerService players;

    @Inject
    public CosmeticsCommand(PaperCosmetics cosmetics, CosmeticMenus menus, CosmeticTexts texts,
                            PlayerService players) {
        this.cosmetics = cosmetics;
        this.menus = menus;
        this.texts = texts;
        this.players = players;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        CosmeticSubCommands commands = commands();
        Predicate<String> permissions = sender::hasPermission;
        if (args.length == 0) {
            if (sender instanceof Player player && commands.opens(permissions)) {
                menus.open(player);
            } else {
                help(sender, commands, permissions);
            }
            return true;
        }
        String action = CosmeticSubCommands.action(args[0]);
        Optional<CosmeticSlot> category = category(action);
        if (category.isPresent() && commands.opens(permissions)) {
            if (sender instanceof Player player) {
                menus.openCategory(player, category.get(), null);
            } else {
                texts.send(sender, "general.players-only");
            }
            return true;
        }
        if (!commands.allowed(action, permissions)) {
            help(sender, commands, permissions);
            return true;
        }
        switch (action) {
            case CosmeticSubCommands.HIDE -> hide(sender);
            case CosmeticSubCommands.GIVE -> change(sender, args, true);
            case CosmeticSubCommands.TAKE -> change(sender, args, false);
            case CosmeticSubCommands.PLAY -> play(sender, args);
            default -> help(sender, commands, permissions);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        CosmeticSubCommands commands = commands();
        Predicate<String> permissions = sender::hasPermission;
        if (args.length <= 1) {
            return commands.suggestions(permissions, args.length == 0 ? "" : args[0], categoryIds());
        }
        String action = CosmeticSubCommands.action(args[0]);
        if (CosmeticSubCommands.PLAY.equals(action)) {
            if (!commands.allowed(action, permissions)) {
                return List.of();
            }
            List<String> online = Bukkit.getOnlinePlayers().stream()
                    .filter(player -> !(sender instanceof Player viewer) || viewer.canSee(player))
                    .map(Player::getName).toList();
            return PlayArguments.suggestions(args.length - 1, args[args.length - 1], online,
                    effectSlots().stream().map(CosmeticSlot::id).toList());
        }
        boolean staff = (CosmeticSubCommands.GIVE.equals(action) || CosmeticSubCommands.TAKE.equals(action))
                && commands.allowed(action, permissions);
        if (!staff) {
            return List.of();
        }
        if (args.length == 2) {
            UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
            return players.suggestions(viewer, args[1]);
        }
        if (args.length == 3) {
            String typed = args[2].toLowerCase(Locale.ROOT);
            return cosmetics.cosmetics().stream().map(Cosmetic::id).filter(id -> id.startsWith(typed)).toList();
        }
        return List.of();
    }

    private void hide(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            texts.send(sender, "general.players-only");
            return;
        }
        boolean hide = !cosmetics.cached(player.getUniqueId()).map(PlayerCosmetics::hidesOthers).orElse(false);
        cosmetics.hideOthers(player.getUniqueId(), hide).whenComplete((hidden, error) -> texts.send(player,
                error != null ? "cosmetics.failed" : hidden ? "cosmetics.hide-on" : "cosmetics.hide-off"));
    }

    private void change(CommandSender sender, String[] args, boolean give) {
        if (args.length != 3) {
            texts.send(sender, give ? "cosmetics.usage-give" : "cosmetics.usage-take");
            return;
        }
        String id = args[2].toLowerCase(Locale.ROOT);
        Optional<Cosmetic> cosmetic = cosmetics.cosmetic(id);
        if (cosmetic.isEmpty()) {
            texts.send(sender, "cosmetics.unknown", Placeholder.unparsed("id", args[2]));
            return;
        }
        UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
        String typed = args[1];
        players.resolve(viewer, typed).whenComplete((found, error) -> {
            if (error != null || found == null || found.isEmpty()) {
                texts.send(sender, "general.player-not-found", Placeholder.unparsed("player", typed));
                return;
            }
            NetworkPlayer target = found.get();
            String shown = players.displayName(viewer, target);
            (give ? cosmetics.unlock(target.uuid(), id, CosmeticSource.STAFF) : cosmetics.revoke(target.uuid(), id))
                    .whenComplete((result, failure) -> {
                        if (failure != null || result == null) {
                            texts.send(sender, "cosmetics.failed");
                            return;
                        }
                        Component name = texts.name(cosmetic.get(), cosmetics.rarityOf(cosmetic.get()),
                                texts.language(sender), viewer);
                        texts.send(sender, CosmeticMessages.key(give ? Action.GIVE : Action.TAKE, result.status()),
                                texts.cosmetic(name), Placeholder.unparsed("player", shown),
                                Placeholder.unparsed("id", id));
                        if (give && result.status() == CosmeticResult.Status.DONE) {
                            cosmetics.tellUnlocked(target.uuid(), result);
                        }
                    });
        });
    }

    private void play(CommandSender sender, String[] args) {
        Optional<PlayArguments> parsed = PlayArguments.parse(args);
        if (parsed.isEmpty()) {
            texts.send(sender, "cosmetics.usage-play");
            return;
        }
        PlayArguments play = parsed.get();
        List<CosmeticSlot> slots = effectSlots();
        Optional<CosmeticSlot> slot = slots.stream().filter(found -> found.id().equals(play.slot())).findFirst();
        if (slot.isEmpty()) {
            texts.send(sender, "cosmetics.play-slot", Placeholder.unparsed("category", play.slot()),
                    Placeholder.unparsed("categories", String.join(", ", slots.stream().map(CosmeticSlot::id)
                            .toList())));
            return;
        }
        Player target = target(play.player());
        if (target == null) {
            texts.send(sender, "cosmetics.play-offline", Placeholder.unparsed("player", play.player()));
            return;
        }
        Location at = switch (play.where()) {
            case PLAYER -> null;
            case HERE -> here(sender, target);
            case COORDINATES -> {
                Location base = target.getLocation();
                yield new Location(target.getWorld(), play.x().resolve(base.getX()), play.y().resolve(base.getY()),
                        play.z().resolve(base.getZ()), base.getYaw(), 0.0f);
            }
        };
        Locale language = texts.language(sender);
        Component category = texts.category(cosmetics.catalog().category(slot.get()), language);
        Optional<Cosmetic> played = cosmetics.playCommand(target, slot.get(), at);
        if (played.isEmpty()) {
            texts.send(sender, "cosmetics.play-none", Placeholder.unparsed("player", target.getName()),
                    Placeholder.component("category", category));
            return;
        }
        UUID viewer = sender instanceof Player player ? player.getUniqueId() : null;
        texts.send(sender, "cosmetics.played", Placeholder.unparsed("player", target.getName()),
                texts.cosmetic(texts.name(played.get(), cosmetics.rarityOf(played.get()), language, viewer)));
    }

    private static Player target(String typed) {
        Player exact = Bukkit.getPlayerExact(typed);
        if (exact != null) {
            return exact;
        }
        try {
            return Bukkit.getPlayer(UUID.fromString(typed));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static Location here(CommandSender sender, Player target) {
        if (sender instanceof Entity entity && entity.getWorld().equals(target.getWorld())) {
            return entity.getLocation();
        }
        if (sender instanceof BlockCommandSender block && block.getBlock().getWorld().equals(target.getWorld())) {
            return block.getBlock().getLocation().add(0.5, 1.0, 0.5);
        }
        return target.getLocation();
    }

    private List<CosmeticSlot> effectSlots() {
        return cosmetics.catalog().categories().stream().map(CosmeticCategory::slot)
                .filter(slot -> slot.effect() && !slot.equals(CosmeticSlot.PROJECTILE_TRAIL)).toList();
    }

    private void help(CommandSender sender, CosmeticSubCommands commands, Predicate<String> permissions) {
        List<Component> lines = new ArrayList<>();
        lines.add(texts.render(sender, "cosmetics.help-header"));
        if (commands.opens(permissions)) {
            lines.add(texts.render(sender, "cosmetics.help-open"));
            lines.add(texts.render(sender, "cosmetics.help-category"));
        }
        for (String action : commands.visible(permissions)) {
            if (!CosmeticSubCommands.HELP.equals(action)) {
                lines.add(texts.render(sender, "cosmetics.help-" + action));
            }
        }
        sender.sendMessage(Component.join(JoinConfiguration.newlines(), lines));
    }

    private Optional<CosmeticSlot> category(String action) {
        return cosmetics.catalog().categories().stream().map(CosmeticCategory::slot)
                .filter(slot -> slot.id().equals(action)).findFirst();
    }

    private List<String> categoryIds() {
        return cosmetics.catalog().categories().stream().map(category -> category.slot().id()).toList();
    }

    private CosmeticSubCommands commands() {
        return new CosmeticSubCommands(cosmetics.settings().hideCommand());
    }
}
