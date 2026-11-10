package net.prismarin.velocity.social;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.velocity.ProxyModules;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.NickDirectory;
import org.slf4j.Logger;

public final class SocialModule {

    private SocialModule() {
        throw new AssertionError();
    }

    public static void register(Object plugin, Logger logger, CoreConfig config, AdminContext admin, Executor executor,
                                Path dataDirectory) {
        boolean friendsOn = config.moduleEnabled(ProxyModules.FRIENDS);
        boolean partyOn = config.moduleEnabled(ProxyModules.PARTY);
        boolean messagesOn = config.moduleEnabled(ProxyModules.PRIVATE_MESSAGES);
        if (!friendsOn && !partyOn && !messagesOn) {
            return;
        }
        SocialSettings settings = SocialSettings.load(dataDirectory.resolve(SocialSettings.FILE_NAME));
        settings.warnings().forEach(warning -> logger.warn("{}: {}", SocialSettings.FILE_NAME, warning));
        SocialContext context = new SocialContext(plugin, admin.proxy(), logger, admin.services(), admin.messages(),
                settings, new SocialStore(admin.services().documents()), admin.vanish(),
                new NickDirectory(admin.services().documents()), admin.channel(), executor, friendsOn, partyOn,
                messagesOn);
        FriendService friends = new FriendService(context);
        PartyService party = new PartyService(context, new PartyManager(settings.party().membersCanInvite()),
                admin.directory(), admin.status());
        MessageService messages = new MessageService(context);
        PresenceTracker presence = new PresenceTracker(context, friends, party, messages);
        admin.proxy().getEventManager().register(plugin, presence);
        presence.start();
        admin.channel().handleSocial(new SocialChannel(context, friends, party, new SocialViews(context, friends, party)));

        CommandManager commands = admin.proxy().getCommandManager();
        List<String> registered = new ArrayList<>();
        if (friendsOn) {
            register(plugin, commands, settings.friends().commands(), SocialCommands.friend(context, friends),
                    registered);
        }
        if (partyOn) {
            register(plugin, commands, settings.party().commands(), SocialCommands.party(context, party, friends),
                    registered);
            register(plugin, commands, settings.party().chatCommands(), SocialCommands.partyChat(context, party),
                    registered);
        }
        if (messagesOn) {
            register(plugin, commands, settings.messages().commands(), SocialCommands.message(context, messages),
                    registered);
            register(plugin, commands, settings.messages().replyCommands(), SocialCommands.reply(context, messages),
                    registered);
            register(plugin, commands, List.of("msgtoggle"), SocialCommands.messageToggle(context, friends),
                    registered);
            register(plugin, commands, List.of("socialspy"), SocialCommands.socialSpy(context, messages), registered);
        }
        logger.info("Social features on: friends {}, party {}, private messages {}, commands {}", friendsOn, partyOn,
                messagesOn, registered);
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
