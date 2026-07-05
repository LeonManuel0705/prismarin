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
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.shared.config.ConfigLoader;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.messaging.MessageBusFactory;
import net.prismarin.velocity.command.HubCommand;
import net.prismarin.velocity.command.MaintenanceCommand;
import net.prismarin.velocity.command.SendAllCommand;
import net.prismarin.velocity.config.ProxyConfigStore;
import net.prismarin.velocity.listener.LobbyRedirectListener;
import net.prismarin.velocity.listener.MaintenanceLoginListener;
import net.prismarin.velocity.listener.PingListener;
import net.prismarin.velocity.locale.ProxyMessages;
import net.prismarin.velocity.motd.MotdBuilder;
import org.slf4j.Logger;

@Plugin(id = "prismarin-core", name = "PrismarinCore", version = "0.1.0", authors = {"Leon"})
public final class PrismarinVelocityPlugin {

    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;

    private ExecutorService executor;
    private MessageBus messageBus;

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
        LocaleService localeService = loadLocale();
        ProxyMessages messages = new ProxyMessages(localeService, Locale.forLanguageTag(coreConfig.defaultLocale()));
        ProxyConfigStore configStore = new ProxyConfigStore(dataDirectory.resolve("proxy.yml"), executor, logger);
        messageBus = MessageBusFactory.create(coreConfig);

        proxy.getEventManager().register(this, new PingListener(configStore, new MotdBuilder()));
        proxy.getEventManager().register(this, new MaintenanceLoginListener(configStore, messages));
        proxy.getEventManager().register(this, new LobbyRedirectListener(proxy, configStore, messages));

        CommandManager commands = proxy.getCommandManager();
        commands.register(commands.metaBuilder("hub").aliases("lobby").plugin(this).build(),
                new HubCommand(proxy, configStore, messages));
        commands.register(commands.metaBuilder("sendall").plugin(this).build(),
                new SendAllCommand(proxy, messages));
        commands.register(commands.metaBuilder("maintenance").plugin(this).build(),
                new MaintenanceCommand(configStore, messages));

        logger.info("PRISMARIN proxy core enabled as '{}' with {}",
                coreConfig.serverId(), messageBus.getClass().getSimpleName());
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (messageBus != null) {
            messageBus.close();
            messageBus = null;
        }
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private CoreConfig loadCoreConfig() {
        Path file = dataDirectory.resolve("config.yml");
        seedResource(file, "config.yml");
        return ConfigLoader.load(file);
    }

    private LocaleService loadLocale() {
        seedResource(dataDirectory.resolve("messages_de.yml"), "locale/messages_de.yml");
        seedResource(dataDirectory.resolve("messages_en.yml"), "locale/messages_en.yml");
        return new YamlLocaleService(dataDirectory);
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
