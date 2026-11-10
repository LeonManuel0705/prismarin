package net.prismarin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.prismarin.api.PrismarinProvider;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.config.ConfigComments;
import net.prismarin.shared.config.ConfigLoader;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.server.YamlServerDirectory;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.shared.status.SelectingConnector;
import net.prismarin.shared.status.SimpleGameStateService;
import net.prismarin.velocity.admin.AdminContext;
import net.prismarin.velocity.admin.AdminSettings;
import net.prismarin.velocity.admin.AlertMonitor;
import net.prismarin.velocity.admin.ChatHistory;
import net.prismarin.velocity.admin.InfoCommands;
import net.prismarin.velocity.admin.ModerationChannel;
import net.prismarin.velocity.admin.ModerationListener;
import net.prismarin.velocity.admin.OperatorRegistry;
import net.prismarin.velocity.admin.ProxyInfoCommand;
import net.prismarin.velocity.admin.PunishmentCommands;
import net.prismarin.velocity.admin.PunishmentFlow;
import net.prismarin.velocity.admin.PunishmentSettings;
import net.prismarin.velocity.admin.ReportCommands;
import net.prismarin.velocity.admin.ReportFlow;
import net.prismarin.velocity.admin.ServerMaintenance;
import net.prismarin.velocity.admin.StaffCommands;
import net.prismarin.velocity.admin.VanishRegistry;
import net.prismarin.velocity.command.HubCommand;
import net.prismarin.velocity.command.MaintenanceCommand;
import net.prismarin.velocity.command.SendAllCommand;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.listener.LobbyRedirectListener;
import net.prismarin.velocity.listener.MaintenanceLoginListener;
import net.prismarin.velocity.listener.PingListener;
import net.prismarin.velocity.locale.PlayerLocaleRegistry;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.motd.MotdBuilder;
import net.prismarin.velocity.server.LobbyBalancer;
import net.prismarin.velocity.server.ProxyConnectorTransport;
import net.prismarin.velocity.server.ProxyServerDirectory;
import net.prismarin.velocity.social.SocialModule;
import org.slf4j.Logger;

@Plugin(id = "prismarin-core", name = "PrismarinCore", version = "0.4.0", authors = {"Leon"})
public final class PrismarinVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ExecutorService executor;
    private CoreServices services;

    @Inject
    public PrismarinVelocityPlugin(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        executor = Executors.newVirtualThreadPerTaskExecutor();
        CoreConfig coreConfig = loadCoreConfig();
        services = CoreServices.start(coreConfig, dataDirectory, executor);
        PlayerLocaleRegistry playerLocales = new PlayerLocaleRegistry();
        proxy.getEventManager().register(this, playerLocales);
        ProxyMessages messages = new ProxyMessages(services.locale(), Locale.forLanguageTag(coreConfig.defaultLocale()),
                playerLocales);
        ProxyConfigStore configStore = new ProxyConfigStore(dataDirectory.resolve("proxy.yml"), executor, logger);
        VanishRegistry vanish = new VanishRegistry();
        ModerationChannel moderationChannel = new ModerationChannel(vanish, playerLocales);
        proxy.getChannelRegistrar().register(ModerationChannel.ID);
        proxy.getEventManager().register(this, vanish);
        proxy.getEventManager().register(this, moderationChannel);
        ServerDirectory directory = new ProxyServerDirectory(proxy,
                new YamlServerDirectory(dataDirectory.resolve("servers.yml")), configStore.config().lobbyServer());
        LobbyBalancer lobbies = new LobbyBalancer(proxy, directory, configStore,
                coreConfig.moduleEnabled(ProxyModules.LOBBY_BALANCING));

        if (coreConfig.moduleEnabled(ProxyModules.MOTD)) {
            proxy.getEventManager().register(this, new PingListener(configStore, new MotdBuilder(services.formatter())));
        }
        if (coreConfig.moduleEnabled(ProxyModules.LOBBY_FALLBACK)) {
            proxy.getEventManager().register(this, new LobbyRedirectListener(lobbies, messages));
        }

        CommandManager commands = proxy.getCommandManager();
        if (coreConfig.moduleEnabled(ProxyModules.HUB_COMMAND)) {
            commands.register(commands.metaBuilder("hub").aliases("lobby").plugin(this).build(),
                    new HubCommand(lobbies, messages));
        }
        if (coreConfig.moduleEnabled(ProxyModules.SENDALL_COMMAND)) {
            commands.register(commands.metaBuilder("sendall").plugin(this).build(),
                    new SendAllCommand(proxy, messages));
        }
        ServerMaintenance serverMaintenance = coreConfig.moduleEnabled(ProxyModules.SERVER_MAINTENANCE)
                ? new ServerMaintenance(services.documents(), directory, messages) : null;
        if (serverMaintenance != null) {
            proxy.getEventManager().register(this, serverMaintenance);
        }
        if (coreConfig.moduleEnabled(ProxyModules.MAINTENANCE)) {
            proxy.getEventManager().register(this, new MaintenanceLoginListener(configStore, messages));
            commands.register(commands.metaBuilder("maintenance").plugin(this).build(),
                    new MaintenanceCommand(configStore, messages, serverMaintenance));
        }

        NetworkStatusService networkStatus = new NetworkStatusService(directory, services.messageBus(), executor);
        lobbies.start(this, networkStatus, serverMaintenance);
        NetworkConnector connector = new SelectingConnector(directory, networkStatus, new ProxyConnectorTransport(proxy));
        PrismarinProvider.register(services.prismarin(directory, networkStatus, new SimpleGameStateService(),
                connector));
        AdminContext admin = new AdminContext(proxy, services, messages, vanish, moderationChannel, connector,
                directory, networkStatus, AdminSettings.load(dataDirectory.resolve("admin.yml")));
        registerAdmin(coreConfig, admin);
        SocialModule.register(this, logger, coreConfig, admin, executor, dataDirectory);
        logger.info("PRISMARIN proxy core enabled as '{}' with {}, systems {} and modules {}",
                coreConfig.serverId(), services.messageBus().getClass().getSimpleName(),
                services.enabledSystems().stream().sorted().toList(),
                ProxyModules.ALL.stream().filter(coreConfig::moduleEnabled).toList());
    }

    private void registerAdmin(CoreConfig config, AdminContext context) {
        CommandManager commands = proxy.getCommandManager();
        Map<String, Map.Entry<List<String>, SimpleCommand>> available = new LinkedHashMap<>();
        available.put(ProxyModules.STAFF_CHAT, Map.entry(List.of("sc", "staffchat"), StaffCommands.staffChat(context)));
        available.put(ProxyModules.STAFF_LIST, Map.entry(List.of("team", "staff"), StaffCommands.team(context)));
        available.put(ProxyModules.FIND, Map.entry(List.of("find"), StaffCommands.find(context)));
        available.put(ProxyModules.SEND, Map.entry(List.of("send"), StaffCommands.send(context)));
        available.put(ProxyModules.GOTO, Map.entry(List.of("goto"), StaffCommands.goTo(context)));
        available.put(ProxyModules.ALERT, Map.entry(List.of("alert", "broadcast"), InfoCommands.alert(context)));
        available.put(ProxyModules.PLAYER_INFO, Map.entry(List.of("pinfo", "playerinfo"), InfoCommands.playerInfo(context)));
        available.put(ProxyModules.AUDIT, Map.entry(List.of("audit"), InfoCommands.audit(context)));
        available.put(ProxyModules.NETEXEC, Map.entry(List.of("netexec"), InfoCommands.netExec(context)));
        available.forEach((module, entry) -> {
            if (config.moduleEnabled(module)) {
                register(commands, entry.getKey(), entry.getValue());
            }
        });
        if (config.moduleEnabled(ProxyModules.REPORTS)) {
            ChatHistory chatHistory = null;
            if (context.settings().chatEvidence().enabled()) {
                chatHistory = new ChatHistory(Clock.systemUTC(), context.settings().chatEvidence().windowMillis());
                proxy.getEventManager().register(this, chatHistory);
            }
            ReportFlow reportFlow = new ReportFlow(context, chatHistory);
            proxy.getEventManager().register(this, reportFlow);
            register(commands, List.of("report"), ReportCommands.report(context, reportFlow));
            register(commands, List.of("reports"), ReportCommands.reports(context, reportFlow));
        }
        if (config.moduleEnabled(ProxyModules.PUNISHMENTS)) {
            PunishmentFlow punishments = new PunishmentFlow(context,
                    PunishmentSettings.load(dataDirectory.resolve(PunishmentSettings.FILE_NAME)));
            proxy.getEventManager().register(this, new ModerationListener(context, punishments));
            PunishmentCommands.all(context, punishments).forEach((names, command) -> register(commands, names, command));
        } else {
            proxy.getEventManager().register(this, new Object() {
                @Subscribe
                public void onPostLogin(PostLoginEvent event) {
                    context.services().rememberName(event.getPlayer().getUniqueId(), event.getPlayer().getUsername());
                }
            });
        }
        OperatorRegistry operators = null;
        if (context.settings().trustBackendOperators()) {
            operators = new OperatorRegistry();
            proxy.getEventManager().register(this, operators);
            context.channel().trustOperators(operators);
            logger.info("Backend operators get all prismarin.* permissions on the proxy");
        }
        register(commands, List.of("prismarinproxy", "pproxy"), new ProxyInfoCommand(context, dataDirectory,
                proxy.getPluginManager().fromInstance(this).flatMap(container -> container.getDescription().getVersion())
                        .orElse("?"), operators));
        if (config.moduleEnabled(ProxyModules.NETWORK_ALERTS)) {
            proxy.getScheduler().buildTask(this, new AlertMonitor(context))
                    .delay(30, TimeUnit.SECONDS)
                    .repeat(context.settings().alertIntervalSeconds(), TimeUnit.SECONDS)
                    .schedule();
        }
    }

    private void register(CommandManager commands, List<String> names, SimpleCommand command) {
        commands.register(commands.metaBuilder(names.getFirst())
                .aliases(names.subList(1, names.size()).toArray(String[]::new))
                .plugin(this)
                .build(), command);
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        PrismarinProvider.unregister();
        if (services != null) {
            services.close();
            services = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private CoreConfig loadCoreConfig() {
        Path file = dataDirectory.resolve("config.yml");
        seedResource(file, "config.yml");
        CoreConfig config = ConfigLoader.load(file, ProxyModules.ALL);
        ConfigComments.apply(file, Map.of());
        return config;
    }

    private void seedResource(Path file, String resourceName) {
        try {
            Files.createDirectories(dataDirectory);
            if (Files.exists(file)) {
                return;
            }
            try (InputStream resource = getClass().getClassLoader().getResourceAsStream(resourceName)) {
                if (resource != null) {
                    Files.copy(resource, file);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
