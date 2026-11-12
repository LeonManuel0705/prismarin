package net.prismarin.velocity.queue;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.prismarin.api.admin.ReloadService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.queue.QueueApi;
import net.prismarin.api.server.NetworkServer;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.velocity.ProxyModules;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.social.FollowPlan;
import net.prismarin.velocity.social.SocialSettings;
import org.slf4j.Logger;

public final class QueueModule {

    private QueueModule() {
        throw new AssertionError();
    }

    public static QueueManager create(Object plugin, ProxyServer proxy, Logger logger, CoreConfig config,
                                      ServerDirectory directory, NetworkStatusService status, ProxyMessages messages,
                                      EventBus events, Path dataDirectory) {
        if (!config.moduleEnabled(ProxyModules.QUEUE)) {
            return null;
        }
        Path file = dataDirectory.resolve(QueueSettings.FILE_NAME);
        QueueSettings settings = QueueSettings.load(file);
        settings.warnings().forEach(warning -> logger.warn("{}: {}", QueueSettings.FILE_NAME, warning));
        return new QueueManager(plugin, proxy, logger, directory, status, messages, events, settings, file,
                Clock.systemUTC());
    }

    public static void start(QueueManager queue, Object plugin, ProxyServer proxy, Logger logger,
                             ProxyMessages messages, Predicate<String> maintenance, SocialSettings social,
                             ReloadService reloads) {
        queue.start(maintenance, follows(social));
        QueueApi.register(queue);
        reloads.register("queue", queue::reload);
        proxy.getEventManager().register(plugin, ProxyShutdownEvent.class, event -> {
            QueueApi.unregister();
            queue.stop();
        });
        QueueSettings settings = queue.settings();
        CommandManager commands = proxy.getCommandManager();
        List<String> registered = new ArrayList<>();
        register(plugin, commands, settings.commands(), QueueCommands.queue(queue, messages, Clock.systemUTC()),
                registered);
        if (settings.subCommands().on("leave")) {
            register(plugin, commands, settings.leaveCommands(), QueueCommands.leaveShortcut(queue, messages),
                    registered);
        }
        logger.info("Queue on with party units {}, display {}, commands {}", settings.party().together(),
                settings.display().bossBar() ? "boss bar" : settings.display().actionBar() ? "action bar" : "chat",
                registered);
    }

    static Predicate<String> follows(SocialSettings social) {
        if (social == null) {
            return group -> false;
        }
        SocialSettings.Party party = social.party();
        return group -> FollowPlan.follows(party.follow(), party.followIntoLobbies(), party.followSkipGroups(), group,
                NetworkServer.GROUP_LOBBY.equalsIgnoreCase(group));
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
