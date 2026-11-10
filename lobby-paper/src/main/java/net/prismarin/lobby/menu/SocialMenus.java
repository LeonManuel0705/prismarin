package net.prismarin.lobby.menu;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.social.PartyRole;
import net.prismarin.core.gui.ItemBuilder;
import net.prismarin.core.gui.Menu;
import net.prismarin.core.gui.MenuButton;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.locale.PlayerLocales;
import net.prismarin.core.social.SocialClient;
import net.prismarin.core.social.SocialView;
import net.prismarin.lobby.LobbyPlugin;
import net.prismarin.lobby.config.ProfileLayout;
import net.prismarin.shared.text.TimeAgo;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

@Singleton
public final class SocialMenus {

    private static final int SMALL_ROWS = 3;
    private static final int SMALL_INFO = 4;
    private static final int SMALL_LEFT = 11;
    private static final int SMALL_MIDDLE = 13;
    private static final int SMALL_RIGHT = 15;
    private static final int SMALL_BACK = 18;
    private static final int SMALL_CLOSE = 26;
    private static final int PARTY_INVITE = 47;
    private static final int PARTY_WARP = 48;
    private static final int PARTY_LEAVE = 50;
    private static final int PARTY_DISBAND = 51;
    private static final int PARTY_SETTINGS = 52;
    private static final int MAX_INCOMING = 21;
    private static final int MAX_OUTGOING = 7;
    private static final Sound SUCCESS = Sound.sound(Key.key("block.note_block.pling"), Sound.Source.MASTER,
            0.5f, 1.6f);

    private final LobbyPlugin plugin;
    private final MenuService menus;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final MenuKit kit;
    private final SocialClient client;
    private final ServerDirectory directory;
    private final Provider<LobbyMenus> lobbyMenus;

    @Inject
    public SocialMenus(LobbyPlugin plugin, MenuService menus, LocaleService locale, ProfileService profiles,
                       MenuKit kit, SocialClient client, ServerDirectory directory, Provider<LobbyMenus> lobbyMenus) {
        this.plugin = plugin;
        this.menus = menus;
        this.locale = locale;
        this.profiles = profiles;
        this.kit = kit;
        this.client = client;
        this.directory = directory;
        this.lobbyMenus = lobbyMenus;
    }

    public List<MenuButton> profileEntry(Player player, Locale target, ProfileLayout.Entry.Kind kind) {
        if (!client.known(player)) {
            client.hello(player).exceptionally(error -> null);
            return List.of();
        }
        SocialView.Features features = client.features(player);
        if (kind == ProfileLayout.Entry.Kind.FRIENDS && features.friends()) {
            return List.of(new MenuButton(ItemBuilder.of(Material.TOTEM_OF_UNDYING)
                    .name(locale.render("lobby.profile.friends.name", target))
                    .lore(locale.render("lobby.profile.friends.lore", target),
                            locale.render("lobby.profile.friends.click", target))
                    .build(), context -> openFriends(context.player(), 0)));
        }
        if (kind == ProfileLayout.Entry.Kind.PARTY && features.party()) {
            return List.of(new MenuButton(ItemBuilder.of(Material.CAKE)
                    .name(locale.render("lobby.profile.party.name", target))
                    .lore(locale.render("lobby.profile.party.lore", target),
                            locale.render("lobby.profile.party.click", target))
                    .build(), context -> openParty(context.player())));
        }
        return List.of();
    }

    public void openFriends(Player player, int page) {
        call(player, client.view(player, SocialClient.VIEW_FRIENDS, page), view -> renderFriends(player, view));
    }

    public void openParty(Player player) {
        call(player, client.view(player, SocialClient.VIEW_PARTY, 0), view -> renderParty(player, view));
    }

    public void openRequests(Player player) {
        call(player, client.view(player, SocialClient.VIEW_REQUESTS, 0), view -> renderRequests(player, view));
    }

    public void openSettings(Player player, Consumer<Player> back) {
        call(player, client.view(player, SocialClient.VIEW_SETTINGS, 0), view -> renderSettings(player, view, back));
    }

    public void openPartySettings(Player player) {
        call(player, client.view(player, SocialClient.VIEW_PARTY, 0), view -> renderPartySettings(player, view));
    }

    public void openPartyInvite(Player player, int page) {
        call(player, client.view(player, SocialClient.VIEW_PARTY_INVITE, page),
                view -> renderPartyInvite(player, view));
    }

    private void renderFriends(Player player, SocialView view) {
        Locale target = locale(player);
        SocialView.FriendPage page = view.friends();
        if (page == null) {
            player.sendMessage(locale.render("friends.menu.disabled", target));
            return;
        }
        Menu menu = new Menu(locale.render("friends.menu.title", target,
                Placeholder.unparsed("online", String.valueOf(page.online())),
                Placeholder.unparsed("total", String.valueOf(page.total()))), SocialLayout.ROWS);
        kit.fill(menu);
        menu.button(SocialLayout.INFO, kit.decoration(ItemBuilder.of(Material.TOTEM_OF_UNDYING)
                .name(locale.render("friends.menu.info.name", target))
                .lore(locale.render("friends.menu.info.lore", target,
                                Placeholder.unparsed("online", String.valueOf(page.online())),
                                Placeholder.unparsed("total", String.valueOf(page.total())),
                                Placeholder.unparsed("limit", String.valueOf(page.limit()))),
                        locale.render("friends.menu.info.page", target,
                                Placeholder.unparsed("page", String.valueOf(page.page() + 1)),
                                Placeholder.unparsed("pages", String.valueOf(page.pages()))))
                .build()));
        List<Integer> slots = SocialLayout.contentSlots(page.entries().size());
        boolean canInvite = view.features().party() && page.canInvite();
        long now = System.currentTimeMillis();
        for (int i = 0; i < slots.size(); i++) {
            SocialView.Friend friend = page.entries().get(i);
            menu.button(slots.get(i), new MenuButton(friendIcon(target, friend, now),
                    context -> openFriend(context.player(), friend, page.page(), canInvite)));
        }
        if (page.entries().isEmpty()) {
            menu.button(SocialLayout.EMPTY, kit.decoration(ItemBuilder.of(Material.WRITABLE_BOOK)
                    .name(locale.render("friends.menu.empty.name", target))
                    .lore(locale.render("friends.menu.empty.lore", target))
                    .build()));
        }
        menu.button(SocialLayout.BACK, kit.back(target, back -> lobbyMenus.get().openProfile(back)));
        int requests = view.counts().requests();
        menu.button(SocialLayout.REQUESTS, new MenuButton(ItemBuilder.of(Material.WRITABLE_BOOK)
                .amount(SocialLayout.badge(requests))
                .glow(requests > 0)
                .name(locale.render("friends.menu.requests.name", target,
                        Placeholder.unparsed("count", String.valueOf(requests))))
                .lore(locale.render("friends.menu.requests.lore", target))
                .build(), context -> openRequests(context.player())));
        if (SocialLayout.hasPrevious(page.page())) {
            menu.button(SocialLayout.PREVIOUS, new MenuButton(ItemBuilder.of(Material.ARROW)
                    .name(locale.render("menu.previous", target)).build(),
                    context -> openFriends(context.player(), page.page() - 1)));
        }
        if (SocialLayout.hasNext(page.page(), page.pages())) {
            menu.button(SocialLayout.NEXT, new MenuButton(ItemBuilder.of(Material.ARROW)
                    .name(locale.render("menu.next", target)).build(),
                    context -> openFriends(context.player(), page.page() + 1)));
        }
        if (view.features().party()) {
            menu.button(SocialLayout.PARTY, new MenuButton(ItemBuilder.of(Material.CAKE)
                    .name(locale.render("lobby.profile.party.name", target))
                    .lore(locale.render("lobby.profile.party.lore", target))
                    .build(), context -> openParty(context.player())));
        }
        menu.button(SocialLayout.SETTINGS, settingsButton(target,
                back -> openFriends(back, page.page())));
        menu.button(SocialLayout.CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private ItemStack friendIcon(Locale target, SocialView.Friend friend, long now) {
        List<Component> lore = new ArrayList<>();
        if (friend.online()) {
            lore.add(locale.render("friends.menu.friend.server", target,
                    Placeholder.component("server", serverName(friend.server()))));
        } else {
            lore.add(locale.render("friends.menu.friend.last-seen", target,
                    Placeholder.component("time", time(target, TimeAgo.since(friend.lastSeen(), now)))));
        }
        lore.add(Component.empty());
        lore.add(locale.render("friends.menu.friend.click", target));
        return head(friend.uuid(), true)
                .name(locale.render(friend.online() ? "friends.menu.friend.online" : "friends.menu.friend.offline",
                        target, Placeholder.unparsed("player", friend.name())))
                .lore(lore)
                .build();
    }

    private void openFriend(Player player, SocialView.Friend friend, int page, boolean canInvite) {
        Locale target = locale(player);
        Menu menu = new Menu(locale.render("friends.menu.friend.title", target,
                Placeholder.unparsed("player", friend.name())), SMALL_ROWS);
        kit.fill(menu);
        menu.button(SMALL_INFO, kit.decoration(friendIcon(target, friend, System.currentTimeMillis())));
        if (friend.online() && friend.jump()) {
            menu.button(SMALL_LEFT, new MenuButton(ItemBuilder.of(Material.ENDER_PEARL)
                    .name(locale.render("friends.menu.action.jump", target))
                    .lore(locale.render("friends.menu.action.jump-lore", target,
                            Placeholder.component("server", serverName(friend.server()))))
                    .glow(true)
                    .build(), context -> {
                        kit.closeLater(context.player());
                        client.act(context.player(), "friend-jump", friend.uuid(), SocialClient.VIEW_NONE, 0)
                                .exceptionally(error -> null);
                    }));
        } else {
            menu.button(SMALL_LEFT, kit.decoration(ItemBuilder.of(Material.GRAY_DYE)
                    .name(locale.render(friend.online() ? "friends.menu.action.jump-off"
                            : "friends.menu.action.jump-offline", target))
                    .build()));
        }
        if (canInvite && friend.online()) {
            menu.button(SMALL_MIDDLE, new MenuButton(ItemBuilder.of(Material.CAKE)
                    .name(locale.render("friends.menu.action.invite", target))
                    .lore(locale.render("friends.menu.action.invite-lore", target))
                    .build(), context -> act(context.player(), "party-invite", friend.uuid(),
                    SocialClient.VIEW_FRIENDS, page, view -> renderFriends(context.player(), view))));
        }
        menu.button(SMALL_RIGHT, new MenuButton(ItemBuilder.of(Material.RED_DYE)
                .name(locale.render("friends.menu.action.remove", target))
                .lore(locale.render("friends.menu.action.remove-lore", target))
                .build(), context -> confirmRemove(context.player(), friend, page, canInvite)));
        menu.button(SMALL_BACK, kit.back(target, back -> openFriends(back, page)));
        menu.button(SMALL_CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private void confirmRemove(Player player, SocialView.Friend friend, int page, boolean canInvite) {
        Locale target = locale(player);
        Menu menu = new Menu(locale.render("friends.menu.confirm.title", target,
                Placeholder.unparsed("player", friend.name())), SMALL_ROWS);
        kit.fill(menu);
        menu.button(SMALL_MIDDLE, kit.decoration(head(friend.uuid(), true)
                .name(locale.render("friends.menu.friend.offline", target,
                        Placeholder.unparsed("player", friend.name())))
                .build()));
        menu.button(SMALL_LEFT, new MenuButton(ItemBuilder.of(Material.RED_CONCRETE)
                .name(locale.render("friends.menu.confirm.yes", target,
                        Placeholder.unparsed("player", friend.name())))
                .build(), context -> act(context.player(), "friend-remove", friend.uuid(), SocialClient.VIEW_FRIENDS,
                page, view -> renderFriends(context.player(), view))));
        menu.button(SMALL_RIGHT, new MenuButton(ItemBuilder.of(Material.LIME_CONCRETE)
                .name(locale.render("friends.menu.confirm.no", target))
                .build(), context -> openFriend(context.player(), friend, page, canInvite)));
        menus.open(player, menu);
    }

    private void renderRequests(Player player, SocialView view) {
        Locale target = locale(player);
        SocialView.Requests requests = view.requests();
        if (requests == null) {
            player.sendMessage(locale.render("friends.menu.disabled", target));
            return;
        }
        Menu menu = new Menu(locale.render("friends.menu.requests.title", target,
                Placeholder.unparsed("count", String.valueOf(requests.incomingTotal()))), SocialLayout.ROWS);
        kit.fill(menu);
        menu.button(SocialLayout.INFO, kit.decoration(ItemBuilder.of(Material.WRITABLE_BOOK)
                .name(locale.render("friends.menu.requests.name", target,
                        Placeholder.unparsed("count", String.valueOf(requests.incomingTotal()))))
                .lore(locale.render("friends.menu.requests.help", target))
                .build()));
        long now = System.currentTimeMillis();
        List<SocialView.Request> incoming = requests.incoming().subList(0,
                Math.min(MAX_INCOMING, requests.incoming().size()));
        List<Integer> slots = SocialLayout.slots(1, 3, incoming.size());
        for (int i = 0; i < slots.size(); i++) {
            SocialView.Request request = incoming.get(i);
            ItemStack icon = head(request.uuid(), true)
                    .name(locale.render("friends.menu.request.name", target,
                            Placeholder.unparsed("player", request.name())))
                    .lore(locale.render("friends.menu.request.time", target,
                                    Placeholder.component("time", time(target, TimeAgo.since(request.at(), now)))),
                            Component.empty(),
                            locale.render("friends.menu.request.click", target))
                    .build();
            menu.button(slots.get(i), new MenuButton(icon, context -> act(context.player(),
                    context.clickType().isRightClick() ? "friend-deny" : "friend-accept", request.uuid(),
                    SocialClient.VIEW_REQUESTS, 0, next -> renderRequests(context.player(), next))));
        }
        List<SocialView.Request> outgoing = requests.outgoing().subList(0,
                Math.min(MAX_OUTGOING, requests.outgoing().size()));
        List<Integer> outgoingSlots = SocialLayout.slots(4, 1, outgoing.size());
        for (int i = 0; i < outgoingSlots.size(); i++) {
            SocialView.Request request = outgoing.get(i);
            List<Component> lore = new ArrayList<>();
            if (request.expires() > 0) {
                lore.add(locale.render("friends.menu.outgoing.time", target, Placeholder.component("time",
                        time(target, TimeAgo.remaining(request.expires() - now)))));
                lore.add(Component.empty());
            }
            lore.add(locale.render("friends.menu.outgoing.click", target));
            ItemStack icon = ItemBuilder.of(Material.PAPER)
                    .name(locale.render("friends.menu.outgoing.name", target,
                            Placeholder.unparsed("player", request.name())))
                    .lore(lore)
                    .build();
            menu.button(outgoingSlots.get(i), new MenuButton(icon, context -> act(context.player(), "friend-cancel",
                    request.uuid(), SocialClient.VIEW_REQUESTS, 0, next -> renderRequests(context.player(), next))));
        }
        if (incoming.isEmpty() && outgoing.isEmpty()) {
            menu.button(SocialLayout.EMPTY, kit.decoration(ItemBuilder.of(Material.BOOK)
                    .name(locale.render("friends.menu.requests.empty", target))
                    .build()));
        }
        menu.button(SocialLayout.BACK, kit.back(target, back -> openFriends(back, 0)));
        menu.button(SocialLayout.CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private void renderParty(Player player, SocialView view) {
        Locale target = locale(player);
        SocialView.PartyView party = view.party();
        if (party == null) {
            player.sendMessage(locale.render("party.menu.disabled", target));
            return;
        }
        Menu menu = new Menu(locale.render("party.menu.title", target,
                Placeholder.unparsed("count", String.valueOf(party.members().size())),
                Placeholder.unparsed("max", String.valueOf(party.max()))), SocialLayout.ROWS);
        kit.fill(menu);
        if (party.inParty()) {
            String leaderName = party.members().stream().filter(SocialView.Person::leader)
                    .map(SocialView.Person::name).findFirst().orElse("?");
            menu.button(SocialLayout.INFO, kit.decoration(ItemBuilder.of(Material.CAKE)
                    .name(locale.render("party.menu.info.name", target,
                            Placeholder.unparsed("count", String.valueOf(party.members().size())),
                            Placeholder.unparsed("max", String.valueOf(party.max()))))
                    .lore(locale.render("party.menu.info.leader", target, Placeholder.unparsed("player", leaderName)),
                            locale.render("party.menu.info.role", target,
                                    Placeholder.component("role", role(target, party.role()))))
                    .build()));
            menu.button(SocialLayout.PARTY_OPTIONS, new MenuButton(ItemBuilder.of(Material.WRITABLE_BOOK)
                    .name(locale.render("party.menu.settings.name", target))
                    .lore(locale.render("party.menu.settings.lore", target))
                    .build(), context -> openPartySettings(context.player())));
            List<SocialView.Person> people = new ArrayList<>(party.members());
            int members = people.size();
            people.addAll(party.invited());
            List<Integer> slots = SocialLayout.contentSlots(Math.min(SocialLayout.PER_PAGE, people.size()));
            for (int i = 0; i < slots.size(); i++) {
                SocialView.Person person = people.get(i);
                if (i < members) {
                    menu.button(slots.get(i), memberButton(target, person, party, view.features()));
                } else {
                    menu.button(slots.get(i), invitedButton(target, person, party));
                }
            }
        } else {
            menu.button(SocialLayout.INFO, kit.decoration(ItemBuilder.of(Material.CAKE)
                    .name(locale.render("party.menu.none.name", target))
                    .lore(locale.render("party.menu.none.lore", target))
                    .build()));
            List<Integer> slots = SocialLayout.contentSlots(Math.min(SocialLayout.PER_PAGE, party.incoming().size()));
            for (int i = 0; i < slots.size(); i++) {
                SocialView.Person invite = party.incoming().get(i);
                ItemStack icon = head(invite.uuid(), invite.showSkin())
                        .name(locale.render("party.menu.invite.name", target,
                                Placeholder.unparsed("player", invite.name())))
                        .lore(locale.render("party.menu.invite.size", target,
                                        Placeholder.unparsed("count", String.valueOf(invite.size()))),
                                Component.empty(),
                                locale.render("party.menu.invite.click", target))
                        .glow(true)
                        .build();
                menu.button(slots.get(i), new MenuButton(icon, context -> act(context.player(),
                        context.clickType().isRightClick() ? "party-deny" : "party-accept", invite.ref(),
                        SocialClient.VIEW_PARTY, 0, next -> renderParty(context.player(), next))));
            }
            if (party.incoming().isEmpty()) {
                menu.button(SocialLayout.EMPTY, kit.decoration(ItemBuilder.of(Material.PAPER)
                        .name(locale.render("party.menu.none.hint", target))
                        .build()));
            }
        }
        menu.button(SocialLayout.BACK, kit.back(target, back -> lobbyMenus.get().openProfile(back)));
        if (party.canInvite() && party.friendsEnabled()) {
            menu.button(PARTY_INVITE, new MenuButton(ItemBuilder.of(Material.EMERALD)
                    .name(locale.render("party.menu.invite-friend.name", target))
                    .lore(locale.render("party.menu.invite-friend.lore", target))
                    .build(), context -> openPartyInvite(context.player(), 0)));
        }
        if (party.inParty() && party.canWarp()) {
            menu.button(PARTY_WARP, new MenuButton(ItemBuilder.of(Material.ENDER_EYE)
                    .name(locale.render("party.menu.warp.name", target))
                    .lore(locale.render("party.menu.warp.lore", target))
                    .build(), context -> act(context.player(), "party-warp", null, SocialClient.VIEW_PARTY, 0,
                    next -> renderParty(context.player(), next))));
        }
        if (party.inParty() && party.canDisband()) {
            menu.button(PARTY_DISBAND, new MenuButton(ItemBuilder.of(Material.TNT)
                    .name(locale.render("party.menu.disband.name", target))
                    .lore(locale.render("party.menu.disband.lore", target))
                    .build(), context -> confirmDisband(context.player())));
        }
        if (party.inParty()) {
            menu.button(PARTY_LEAVE, new MenuButton(ItemBuilder.of(Material.OAK_DOOR)
                    .name(locale.render("party.menu.leave.name", target))
                    .lore(locale.render("party.menu.leave.lore", target))
                    .build(), context -> act(context.player(), "party-leave", null, SocialClient.VIEW_PARTY, 0,
                    next -> renderParty(context.player(), next))));
        }
        menu.button(PARTY_SETTINGS, settingsButton(target, this::openParty));
        menu.button(SocialLayout.CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private MenuButton memberButton(Locale target, SocialView.Person person, SocialView.PartyView party,
                                    SocialView.Features features) {
        String key = switch (person.role()) {
            case LEADER -> "party.menu.member.leader";
            case MODERATOR -> "party.menu.member.moderator";
            case MEMBER -> person.self() ? "party.menu.member.self" : "party.menu.member.name";
        };
        List<Component> lore = new ArrayList<>();
        lore.add(locale.render("party.menu.member.role", target,
                Placeholder.component("role", role(target, person.role()))));
        lore.add(person.online()
                ? locale.render("party.menu.member.server", target,
                        Placeholder.component("server", serverName(person.server())))
                : locale.render("party.menu.member.offline", target));
        boolean actions = !person.self() && !memberActions(person, features).isEmpty();
        if (actions) {
            lore.add(Component.empty());
            lore.add(locale.render("party.menu.member.click", target));
        }
        ItemStack icon = head(person.uuid(), person.showSkin())
                .name(locale.render(key, target, Placeholder.unparsed("player", person.name())))
                .lore(lore)
                .glow(person.leader())
                .build();
        if (!actions) {
            return kit.decoration(icon);
        }
        return new MenuButton(icon, context -> openMember(context.player(), person, party, features));
    }

    private MenuButton invitedButton(Locale target, SocialView.Person person, SocialView.PartyView party) {
        List<Component> lore = new ArrayList<>();
        lore.add(locale.render("party.menu.invited.lore", target));
        if (party.canRevoke()) {
            lore.add(Component.empty());
            lore.add(locale.render("party.menu.invited.click", target));
        }
        ItemStack icon = ItemBuilder.of(Material.PAPER)
                .name(locale.render("party.menu.invited.name", target, Placeholder.unparsed("player", person.name())))
                .lore(lore)
                .build();
        if (!party.canRevoke()) {
            return kit.decoration(icon);
        }
        return new MenuButton(icon, context -> act(context.player(), "party-kick", person.ref(),
                SocialClient.VIEW_PARTY, 0, next -> renderParty(context.player(), next)));
    }

    private List<SocialLayout.MemberAction> memberActions(SocialView.Person person, SocialView.Features features) {
        SocialView.Permissions allowed = person.permissions();
        return SocialLayout.memberActions(allowed.promote(), allowed.demote(), allowed.transfer(),
                features.friends() && !person.friend() && !person.masked(), allowed.kick());
    }

    private void openMember(Player player, SocialView.Person person, SocialView.PartyView party,
                            SocialView.Features features) {
        Locale target = locale(player);
        Menu menu = new Menu(locale.render("party.menu.member.title", target,
                Placeholder.unparsed("player", person.name())), SMALL_ROWS);
        kit.fill(menu);
        menu.button(SMALL_INFO, kit.decoration(head(person.uuid(), person.showSkin())
                .name(locale.render("party.menu.member.name", target, Placeholder.unparsed("player", person.name())))
                .lore(locale.render("party.menu.member.role", target,
                        Placeholder.component("role", role(target, person.role()))))
                .build()));
        List<SocialLayout.MemberAction> actions = memberActions(person, features);
        List<Integer> slots = actions.isEmpty() ? List.of() : MenuLayout.centeredRowSlots(1, actions.size());
        for (int i = 0; i < actions.size(); i++) {
            menu.button(slots.get(i), memberAction(target, actions.get(i), person, party, features));
        }
        menu.button(SMALL_BACK, kit.back(target, this::openParty));
        menu.button(SMALL_CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private MenuButton memberAction(Locale target, SocialLayout.MemberAction action, SocialView.Person person,
                                    SocialView.PartyView party, SocialView.Features features) {
        return switch (action) {
            case PROMOTE -> partyButton(target, Material.GOLDEN_HELMET, "party.menu.moderator", person,
                    "party-promote");
            case DEMOTE -> partyButton(target, Material.LEATHER_HELMET, "party.menu.demote", person, "party-demote");
            case KICK -> partyButton(target, Material.IRON_DOOR, "party.menu.kick", person, "party-kick");
            case TRANSFER -> new MenuButton(ItemBuilder.of(Material.GOLDEN_APPLE)
                    .name(locale.render("party.menu.transfer.name", target))
                    .lore(locale.render("party.menu.transfer.lore", target,
                            Placeholder.unparsed("player", person.name())))
                    .build(), context -> confirmTransfer(context.player(), person, party, features));
            case BEFRIEND -> partyButton(target, Material.TOTEM_OF_UNDYING, "party.menu.befriend", person,
                    "friend-add");
        };
    }

    private MenuButton partyButton(Locale target, Material icon, String key, SocialView.Person person, String action) {
        return new MenuButton(ItemBuilder.of(icon)
                .name(locale.render(key + ".name", target))
                .lore(locale.render(key + ".lore", target, Placeholder.unparsed("player", person.name())))
                .build(), context -> act(context.player(), action, person.ref(), SocialClient.VIEW_PARTY, 0,
                next -> renderParty(context.player(), next)));
    }

    private void confirmTransfer(Player player, SocialView.Person person, SocialView.PartyView party,
                                 SocialView.Features features) {
        Locale target = locale(player);
        Menu menu = new Menu(locale.render("party.menu.transfer.title", target), SMALL_ROWS);
        kit.fill(menu);
        menu.button(SMALL_MIDDLE, kit.decoration(head(person.uuid(), person.showSkin())
                .name(locale.render("party.menu.member.name", target, Placeholder.unparsed("player", person.name())))
                .build()));
        menu.button(SMALL_LEFT, new MenuButton(ItemBuilder.of(Material.GOLD_BLOCK)
                .name(locale.render("party.menu.transfer.yes", target, Placeholder.unparsed("player", person.name())))
                .build(), context -> act(context.player(), "party-transfer", person.ref(), SocialClient.VIEW_PARTY, 0,
                next -> renderParty(context.player(), next))));
        menu.button(SMALL_RIGHT, new MenuButton(ItemBuilder.of(Material.LIME_CONCRETE)
                .name(locale.render("party.menu.transfer.no", target)).build(),
                context -> openMember(context.player(), person, party, features)));
        menus.open(player, menu);
    }

    private void renderPartySettings(Player player, SocialView view) {
        Locale target = locale(player);
        SocialView.PartyView party = view.party();
        if (party == null || !party.inParty()) {
            openParty(player);
            return;
        }
        List<SocialView.Setting> settings = party.settings().subList(0, Math.min(9, party.settings().size()));
        Menu menu = new Menu(locale.render("party.menu.settings.title", target), SMALL_ROWS);
        kit.fill(menu);
        if (!settings.isEmpty()) {
            List<Integer> slots = MenuLayout.centeredRowSlots(1, settings.size());
            for (int i = 0; i < settings.size(); i++) {
                SocialView.Setting setting = settings.get(i);
                List<Component> lore = new ArrayList<>();
                for (String option : setting.options()) {
                    lore.add(locale.render(option.equals(setting.value()) ? "friends.menu.setting.selected"
                            : "friends.menu.setting.option", target,
                            Placeholder.component("value", locale.render("party.menu.value." + option, target))));
                }
                lore.add(Component.empty());
                lore.add(party.canSettings()
                        ? locale.render("friends.menu.setting.click", target, Placeholder.component("value",
                                locale.render("party.menu.value." + setting.next(), target)))
                        : locale.render("party.menu.settings.locked", target));
                ItemStack icon = ItemBuilder.of(partySettingIcon(setting.key(), setting.value()))
                        .name(locale.render("party.menu.setting." + setting.key(), target))
                        .lore(lore)
                        .build();
                if (!party.canSettings()) {
                    menu.button(slots.get(i), kit.decoration(icon));
                    continue;
                }
                menu.button(slots.get(i), new MenuButton(icon, context -> call(context.player(),
                        client.partySetting(context.player(), setting.key(), setting.next()),
                        next -> {
                            context.player().playSound(SUCCESS, Sound.Emitter.self());
                            renderPartySettings(context.player(), next);
                        })));
            }
        }
        menu.button(SMALL_BACK, kit.back(target, this::openParty));
        menu.button(SMALL_CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private static Material partySettingIcon(String key, String value) {
        if ("off".equals(value)) {
            return Material.GRAY_DYE;
        }
        return switch (key) {
            case "invite" -> Material.EMERALD;
            case "access" -> "open".equals(value) ? Material.OAK_DOOR : Material.IRON_DOOR;
            case "follow" -> Material.LEAD;
            case "chat" -> Material.OAK_SIGN;
            default -> Material.COMPARATOR;
        };
    }

    private Component role(Locale target, PartyRole role) {
        return locale.render("party.menu.role." + role.id(), target);
    }

    private void confirmDisband(Player player) {
        Locale target = locale(player);
        Menu menu = new Menu(locale.render("party.menu.disband.title", target), SMALL_ROWS);
        kit.fill(menu);
        menu.button(SMALL_MIDDLE, kit.decoration(ItemBuilder.of(Material.TNT)
                .name(locale.render("party.menu.disband.name", target)).build()));
        menu.button(SMALL_LEFT, new MenuButton(ItemBuilder.of(Material.RED_CONCRETE)
                .name(locale.render("party.menu.disband.yes", target)).build(),
                context -> act(context.player(), "party-disband", null, SocialClient.VIEW_PARTY, 0,
                        next -> renderParty(context.player(), next))));
        menu.button(SMALL_RIGHT, new MenuButton(ItemBuilder.of(Material.LIME_CONCRETE)
                .name(locale.render("party.menu.disband.no", target)).build(),
                context -> openParty(context.player())));
        menus.open(player, menu);
    }

    private void renderPartyInvite(Player player, SocialView view) {
        Locale target = locale(player);
        SocialView.Candidates candidates = view.candidates();
        if (candidates == null) {
            player.sendMessage(locale.render("party.menu.disabled", target));
            return;
        }
        Menu menu = new Menu(locale.render("party.menu.invite-friend.title", target), SocialLayout.ROWS);
        kit.fill(menu);
        menu.button(SocialLayout.INFO, kit.decoration(ItemBuilder.of(Material.EMERALD)
                .name(locale.render("party.menu.invite-friend.name", target))
                .lore(locale.render("party.menu.invite-friend.lore", target))
                .build()));
        List<Integer> slots = SocialLayout.contentSlots(candidates.entries().size());
        for (int i = 0; i < slots.size(); i++) {
            SocialView.Candidate candidate = candidates.entries().get(i);
            ItemStack icon = head(candidate.uuid(), true)
                    .name(locale.render("party.menu.candidate.name", target,
                            Placeholder.unparsed("player", candidate.name())))
                    .lore(locale.render("party.menu.candidate.server", target,
                                    Placeholder.component("server", serverName(candidate.server()))),
                            Component.empty(),
                            locale.render("party.menu.candidate.click", target))
                    .build();
            menu.button(slots.get(i), new MenuButton(icon, context -> act(context.player(), "party-invite",
                    candidate.uuid(), SocialClient.VIEW_PARTY_INVITE, candidates.page(),
                    next -> renderPartyInvite(context.player(), next))));
        }
        if (candidates.entries().isEmpty()) {
            menu.button(SocialLayout.EMPTY, kit.decoration(ItemBuilder.of(Material.PAPER)
                    .name(locale.render("party.menu.candidate.empty", target))
                    .build()));
        }
        if (SocialLayout.hasPrevious(candidates.page())) {
            menu.button(SocialLayout.PREVIOUS, new MenuButton(ItemBuilder.of(Material.ARROW)
                    .name(locale.render("menu.previous", target)).build(),
                    context -> openPartyInvite(context.player(), candidates.page() - 1)));
        }
        if (SocialLayout.hasNext(candidates.page(), candidates.pages())) {
            menu.button(SocialLayout.NEXT, new MenuButton(ItemBuilder.of(Material.ARROW)
                    .name(locale.render("menu.next", target)).build(),
                    context -> openPartyInvite(context.player(), candidates.page() + 1)));
        }
        menu.button(SocialLayout.BACK, kit.back(target, this::openParty));
        menu.button(SocialLayout.CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private MenuButton settingsButton(Locale target, Consumer<Player> back) {
        return new MenuButton(ItemBuilder.of(Material.COMPARATOR)
                .name(locale.render("friends.menu.settings.name", target))
                .lore(locale.render("friends.menu.settings.lore", target))
                .build(), context -> openSettings(context.player(), back));
    }

    private void renderSettings(Player player, SocialView view, Consumer<Player> back) {
        Locale target = locale(player);
        List<SocialView.Setting> settings = view.settings().subList(0, Math.min(9, view.settings().size()));
        Menu menu = new Menu(locale.render("friends.menu.settings.title", target), SMALL_ROWS);
        kit.fill(menu);
        if (!settings.isEmpty()) {
            List<Integer> slots = MenuLayout.centeredRowSlots(1, settings.size());
            for (int i = 0; i < settings.size(); i++) {
                SocialView.Setting setting = settings.get(i);
                List<Component> lore = new ArrayList<>();
                for (String option : setting.options()) {
                    lore.add(locale.render(option.equals(setting.value()) ? "friends.menu.setting.selected"
                            : "friends.menu.setting.option", target,
                            Placeholder.component("value", locale.render("friends.value." + option, target))));
                }
                lore.add(Component.empty());
                lore.add(locale.render("friends.menu.setting.click", target,
                        Placeholder.component("value", locale.render("friends.value." + setting.next(), target))));
                ItemStack icon = ItemBuilder.of(settingIcon(setting.key(), setting.value()))
                        .name(locale.render("friends.menu.setting." + setting.key(), target))
                        .lore(lore)
                        .build();
                menu.button(slots.get(i), new MenuButton(icon, context -> call(context.player(),
                        client.setting(context.player(), setting.key(), setting.next()),
                        next -> {
                            context.player().playSound(SUCCESS, Sound.Emitter.self());
                            renderSettings(context.player(), next, back);
                        })));
            }
        }
        menu.button(SMALL_BACK, kit.back(target, back));
        menu.button(SMALL_CLOSE, kit.close(target));
        menus.open(player, menu);
    }

    private static Material settingIcon(String key, String value) {
        if ("off".equals(value) || "nobody".equals(value)) {
            return Material.GRAY_DYE;
        }
        return switch (key) {
            case "requests" -> Material.WRITABLE_BOOK;
            case "notifications" -> Material.BELL;
            case "jump" -> Material.ENDER_PEARL;
            case "party-invites" -> Material.CAKE;
            case "messages" -> Material.PAPER;
            default -> Material.COMPARATOR;
        };
    }

    private void act(Player player, String action, UUID target, String view, int page, Consumer<SocialView> render) {
        call(player, client.act(player, action, target, view, page), next -> {
            player.playSound(SUCCESS, Sound.Emitter.self());
            render.accept(next);
        });
    }

    private void call(Player player, CompletableFuture<SocialView> request, Consumer<SocialView> render) {
        client.onMain(request, (view, error) -> {
            if (!player.isOnline()) {
                return;
            }
            if (error != null) {
                if (!(unwrap(error) instanceof SocialClient.BusyException)) {
                    player.sendMessage(locale.render("friends.menu.unavailable", locale(player)));
                }
                return;
            }
            render.accept(view);
        });
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current != current.getCause()
                && current instanceof CompletionException) {
            current = current.getCause();
        }
        return current;
    }

    private ItemBuilder head(UUID uuid, boolean showSkin) {
        ItemBuilder builder = ItemBuilder.of(Material.PLAYER_HEAD);
        if (showSkin && uuid != null && !SocialView.NOBODY.equals(uuid)) {
            builder.skullOwner(plugin.getServer().getOfflinePlayer(uuid));
        }
        return builder;
    }

    private Component serverName(String server) {
        if (server == null || server.isBlank()) {
            return Component.text("-");
        }
        return directory.byName(server).map(NetworkServer::displayName).map(name -> locale.format(name))
                .orElseGet(() -> Component.text(server));
    }

    private Component time(Locale target, TimeAgo time) {
        return locale.render("friends.time." + time.unit().key(), target,
                Placeholder.unparsed("amount", String.valueOf(time.amount())));
    }

    private Locale locale(Player player) {
        return PlayerLocales.of(player, profiles);
    }
}
