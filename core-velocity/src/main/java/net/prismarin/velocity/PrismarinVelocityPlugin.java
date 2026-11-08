package net.prismarin.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.event.Subscribe;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.prismarin.api.PrismarinProvider;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.shared.config.ConfigLoader;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.server.YamlServerDirectory;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.velocity.command.HubCommand;
import net.prismarin.velocity.command.MaintenanceCommand;
import net.prismarin.velocity.command.SendAllCommand;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.listener.LobbyRedirectListener;
import net.prismarin.velocity.listener.MaintenanceLoginListener;
import net.prismarin.velocity.listener.PingListener;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.motd.MotdBuilder;
import net.prismarin.velocity.server.ProxyServerDirectory;
import org.slf4j.Logger;

@Plugin(id = "prismarin-core", name = "PrismarinCore", version = "0.2.0", authors = {"Leon"})
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
        ProxyMessages messages = new ProxyMessages(services.locale(), Locale.forLanguageTag(coreConfig.defaultLocale()));
        ProxyConfigStore configStore = new ProxyConfigStore(dataDirectory.resolve("proxy.yml"), executor, logger);
        ServerDirectory directory = new ProxyServerDirectory(proxy,
                new YamlServerDirectory(dataDirectory.resolve("servers.yml")), configStore.config().lobbyServer());

        if (coreConfig.moduleEnabled(ProxyModules.MOTD)) {
            proxy.getEventManager().register(this, new PingListener(configStore, new MotdBuilder(services.formatter())));
        }
        if (coreConfig.moduleEnabled(ProxyModules.LOBBY_FALLBACK)) {
            proxy.getEventManager().register(this, new LobbyRedirectListener(proxy, configStore, messages));
        }

        CommandManager commands = proxy.getCommandManager();
        if (coreConfig.moduleEnabled(ProxyModules.HUB_COMMAND)) {
            commands.register(commands.metaBuilder("hub").aliases("lobby").plugin(this).build(),
                    new HubCommand(proxy, configStore, messages));
        }
        if (coreConfig.moduleEnabled(ProxyModules.SENDALL_COMMAND)) {
            commands.register(commands.metaBuilder("sendall").plugin(this).build(),
                    new SendAllCommand(proxy, messages));
        }
        if (coreConfig.moduleEnabled(ProxyModules.MAINTENANCE)) {
            proxy.getEventManager().register(this, new MaintenanceLoginListener(configStore, messages));
            commands.register(commands.metaBuilder("maintenance").plugin(this).build(),
                    new MaintenanceCommand(configStore, messages));
        }

        NetworkStatusService networkStatus = new NetworkStatusService(directory, services.messageBus(), executor);
        PrismarinProvider.register(services.prismarin(directory, networkStatus));
        logger.info("PRISMARIN proxy core enabled as '{}' with {}, systems {} and modules {}",
                coreConfig.serverId(), services.messageBus().getClass().getSimpleName(),
                services.enabledSystems().stream().sorted().toList(),
                ProxyModules.ALL.stream().filter(coreConfig::moduleEnabled).toList());
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
        return ConfigLoader.load(file, ProxyModules.ALL);
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
