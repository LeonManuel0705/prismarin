package net.prismarin.velocity.social;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.social.ClanRole;
import net.prismarin.api.social.SocialApi;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.velocity.ProxyModules;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.NickDirectory;
import net.prismarin.velocity.admin.PlayerInfoLines;
import org.slf4j.Logger;

public final class SocialModule {

    private SocialModule() {
        throw new AssertionError();
    }

    public static SocialSettings register(Object plugin, Logger logger, CoreConfig config, AdminContext admin,
                                          Executor executor, Path dataDirectory) {
        boolean friendsOn = config.moduleEnabled(ProxyModules.FRIENDS);
        boolean partyOn = config.moduleEnabled(ProxyModules.PARTY);
        boolean messagesOn = config.moduleEnabled(ProxyModules.PRIVATE_MESSAGES);
        boolean clansOn = config.moduleEnabled(ProxyModules.CLANS);
        if (!friendsOn && !partyOn && !messagesOn && !clansOn) {
            return null;
        }
        SocialSettings settings = SocialSettings.load(dataDirectory.resolve(SocialSettings.FILE_NAME));
        settings.warnings().forEach(warning -> logger.warn("{}: {}", SocialSettings.FILE_NAME, warning));
        SocialContext context = new SocialContext(plugin, admin.proxy(), logger, admin.services(), admin.messages(),
                settings, new SocialStore(admin.services().documents()), admin.vanish(),
                new NickDirectory(admin.services().documents()), admin.channel(), executor, friendsOn, partyOn,
                messagesOn, clansOn);
        FriendService friends = new FriendService(context);
        PartyService party = new PartyService(context, new PartyManager(settings.party().defaults()),
                admin.directory(), admin.status());
        MessageService messages = new MessageService(context);
        ClanService clans = clansOn ? new ClanService(context, new ClanManager(settings.clans().rights()),
                new ClanStore(admin.services().documents(), admin.services().messageBus())) : null;
        if (clans != null) {
            context.clanLookup(clans.clans()::clanOf);
            PlayerInfoLines.add((viewer, player) -> context.clanShownTo(viewer, "player-info", player)
                    .map(clan -> context.render(viewer, "clan.player-info",
                            Placeholder.component("tag", clans.tag(clan)), Placeholder.unparsed("clan", clan.name()),
                            Placeholder.component("role", context.render(viewer, "clan.role."
                                    + clan.role(player).orElse(ClanRole.MEMBER).id())))));
        }
        PresenceTracker presence = new PresenceTracker(context, friends, party, messages, clans);
        admin.proxy().getEventManager().register(plugin, presence);
        presence.start();
        admin.channel().handleSocial(new SocialChannel(context, friends, party, clans,
                new SocialViews(context, friends, party, clans)));
        SocialApi.register(friendsOn ? new ProxyFriendApi(context, friends) : null,
                partyOn ? new ProxyPartyApi(context, party) : null,
                clans != null ? new ProxyClanApi(context, clans) : null);
        admin.proxy().getEventManager().register(plugin, ProxyShutdownEvent.class, event -> SocialApi.unregister());

        CommandManager commands = admin.proxy().getCommandManager();
        List<String> registered = new ArrayList<>();
        if (friendsOn) {
            register(plugin, commands, settings.friends().commands(), SocialCommands.friend(context, friends),
                    registered);
        }
        if (partyOn) {
            register(plugin, commands, settings.party().commands(), SocialCommands.party(context, party, friends),
                    registered);
            if (settings.party().subCommands().on("chat")) {
                register(plugin, commands, settings.party().chatCommands(), SocialCommands.partyChat(context, party),
                        registered);
            }
        }
        if (messagesOn) {
            register(plugin, commands, settings.messages().commands(), SocialCommands.message(context, messages),
                    registered);
            if (settings.messages().subCommands().on("reply")) {
                register(plugin, commands, settings.messages().replyCommands(), SocialCommands.reply(context, messages),
                        registered);
            }
            if (settings.messages().subCommands().on("toggle")) {
                register(plugin, commands, List.of("msgtoggle"), SocialCommands.messageToggle(context, friends),
                        registered);
            }
        }
        if (settings.messages().staffSpy() && (messagesOn || partyOn || clansOn)) {
            register(plugin, commands, List.of("socialspy"), SocialCommands.socialSpy(context, messages), registered);
        }
        if (clans != null) {
            register(plugin, commands, settings.clans().commands(), ClanCommands.clan(context, clans, friends),
                    registered);
            if (settings.clans().subCommands().on("chat")) {
                register(plugin, commands, settings.clans().chatCommands(), ClanCommands.clanChat(context, clans),
                        registered);
            }
        }
        logger.info("Social features on: friends {}, party {}, clans {}, private messages {}, commands {}", friendsOn,
                partyOn, clansOn, messagesOn, registered);
        return settings;
    }

    private static void register(Object plugin, CommandManager commands, List<String> names, SimpleCommand command,
                                 List<String> registered) {
        commands.register(commands.metaBuilder(names.getFirst())
                .aliases(names.subList(1, names.size()).toArray(String[]::new))
                .plugin(plugin)
                .build(), command);
        registered.add(names.getFirst());
    }
}
