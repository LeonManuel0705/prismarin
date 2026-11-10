package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.nick.NickResult;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.nick.NickManager;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.item.LobbyItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class NickToolMenu {

    private static final int STATUS = 10;
    private static final int RANDOM = 12;
    private static final int AUTO = 14;
    private static final int REMOVE = 16;
    private static final int CLOSE = 22;
    private static final Sound DONE = Sound.sound(Key.key("block.note_block.chime"), Sound.Source.MASTER, 0.6f, 1.5f);

    private final LobbyPlugin plugin;
    private final MenuService menus;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final MenuKit kit;
    private final LobbyItems items;

    @Inject
    public NickToolMenu(LobbyPlugin plugin, MenuService menus, LocaleService locale, ProfileService profiles,
                        MenuKit kit, LobbyItems items) {
        this.plugin = plugin;
        this.menus = menus;
        this.locale = locale;
        this.profiles = profiles;
        this.kit = kit;
        this.items = items;
    }

    public void open(Player player) {
        Locale target = PlayerLocales.of(player, profiles);
        Optional<NickService> service = items.nickService();
        if (service.isEmpty()) {
            player.sendMessage(locale.render("nick.disabled", target));
            return;
        }
        if (!items.permitted(player, LobbyItems.NICK_TOOL_ID)) {
            player.sendMessage(locale.render("general.no-permission", target));
            return;
        }
        NickService nicks = service.get();
        UUID uuid = player.getUniqueId();
        Optional<String> nick = nicks.shownNick(uuid).or(() -> nicks.storedNick(uuid));
        boolean auto = nicks.autoNick(uuid);
        boolean shownHere = nicks.nicksShownHere();
        Menu menu = new Menu(locale.render("lobby.nick-tool.title", target), 3);
        kit.fill(menu);
        menu.button(STATUS, kit.decoration(status(player, target, nick, auto, shownHere)));
        if (player.hasPermission(NickManager.PERMISSION_USE)) {
            menu.button(RANDOM, new MenuButton(ItemBuilder.of(Material.NAME_TAG)
                    .name(locale.render(nick.isPresent() ? "lobby.nick-tool.random.again" : "lobby.nick-tool.random.name",
                            target))
                    .lore(locale.render("lobby.nick-tool.random.lore", target))
                    .glow(true)
                    .build(), context -> run(context.player(), nicks.nickRandom(uuid),
                            result -> report(context.player(), result))));
        }
        if (player.hasPermission(NickManager.PERMISSION_AUTO)) {
            menu.button(AUTO, new MenuButton(ItemBuilder.of(auto ? Material.LIME_DYE : Material.GRAY_DYE)
                    .name(locale.render(auto ? "lobby.nick-tool.auto.on" : "lobby.nick-tool.auto.off", target))
                    .lore(locale.render("lobby.nick-tool.auto.lore", target))
                    .build(), context -> toggleAuto(context.player(), nicks, !auto)));
        }
        if (nick.isPresent()) {
            menu.button(REMOVE, new MenuButton(ItemBuilder.of(Material.BARRIER)
                    .name(locale.render("lobby.nick-tool.remove.name", target))
                    .lore(locale.render("lobby.nick-tool.remove.lore", target,
                            Placeholder.unparsed("nick", nick.get())))
                    .build(), context -> run(context.player(), nicks.unnick(uuid), result -> {
                        report(context.player(), result);
                        if (result.status() == NickResult.Status.REMOVED && nicks.autoNick(uuid)) {
                            context.player().sendMessage(locale.render("nick.auto-still-on",
                                    PlayerLocales.of(context.player(), profiles)));
                        }
                    })));
        }
        menu.button(CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private ItemStack status(Player player, Locale target, Optional<String> nick, boolean auto,
                                                  boolean shownHere) {
        List<Component> lore = new ArrayList<>();
        lore.add(nick.map(name -> locale.render("lobby.nick-tool.status.nicked", target,
                        Placeholder.unparsed("nick", name)))
                .orElseGet(() -> locale.render("lobby.nick-tool.status.none", target)));
        lore.add(locale.render(auto ? "lobby.nick-tool.status.auto-on" : "lobby.nick-tool.status.auto-off", target));
        if (!shownHere) {
            lore.add(Component.empty());
            lore.add(locale.render("lobby.nick-tool.status.where", target));
        }
        if (player.hasPermission(NickManager.PERMISSION_CUSTOM)) {
            lore.add(Component.empty());
            lore.add(locale.render("lobby.nick-tool.status.custom", target));
        }
        return ItemBuilder.of(Material.PLAYER_HEAD)
                .skullOwner(player)
                .name(locale.render("lobby.nick-tool.status.name", target,
                        Placeholder.unparsed("player", player.getName())))
                .lore(lore)
                .build();
    }

    private void toggleAuto(Player player, NickService nicks, boolean enabled) {
        nicks.setAutoNick(player.getUniqueId(), enabled).whenComplete((unused, error) ->
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    Locale target = PlayerLocales.of(player, profiles);
                    player.sendMessage(locale.render(error != null ? "nick.failed"
                            : enabled ? "nick.auto-on" : "nick.auto-off", target));
                    if (error == null && enabled && nicks instanceof NickManager manager && !manager.autoNickUsedHere()) {
                        player.sendMessage(locale.render("nick.auto-not-here", target));
                    }
                    player.playSound(DONE, Sound.Emitter.self());
                    open(player);
                }));
    }

    private void run(Player player, CompletableFuture<NickResult> action, Consumer<NickResult> after) {
        action.whenComplete((result, error) -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            after.accept(error != null ? NickResult.of(NickResult.Status.FAILED) : result);
            open(player);
        }));
    }

    private void report(Player player, NickResult result) {
        Locale target = PlayerLocales.of(player, profiles);
        String key = switch (result.status()) {
            case APPLIED -> "nick.applied";
            case STORED -> "nick.stored";
            case REMOVED -> "nick.removed";
            case NOT_NICKED -> "nick.not-nicked";
            case INVALID_NAME -> "nick.invalid-name";
            case NAME_TAKEN -> "nick.name-taken";
            case NO_FREE_NAME -> "nick.no-free-name";
            case BUSY -> "nick.busy";
            case DISABLED -> "nick.disabled";
            default -> "nick.failed";
        };
        player.sendMessage(locale.render(key, target, Placeholder.unparsed("nick", result.name()),
                Placeholder.unparsed("name", result.name())));
        if (result.success()) {
            player.playSound(DONE, Sound.Emitter.self());
        }
    }
}
