package net.prismarin.core;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.api.Prismarin;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.config.GameStateSettings;
import net.prismarin.core.config.MenuSettings;
import net.prismarin.core.config.VanishSettings;
import net.prismarin.core.network.PaperConnectorTransport;
import net.prismarin.core.permission.PermissionSnapshots;
import net.prismarin.core.rank.DefaultRankFormatProvider;
import net.prismarin.core.rank.LuckPermsRankFormatProvider;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.shared.config.ConfigLoader;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.platform.CoreServices;
import net.prismarin.shared.server.YamlServerDirectory;
import net.prismarin.shared.status.NetworkStatusService;
import net.prismarin.shared.status.SelectingConnector;
import net.prismarin.shared.status.SimpleGameStateService;
import net.prismarin.shared.storage.ProfileRepository;
import net.prismarin.shared.text.TextFormatter;

public final class CoreModule extends AbstractModule {

    private final PrismarinCorePlugin plugin;

    public CoreModule(PrismarinCorePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    protected void configure() {
        bind(PrismarinCorePlugin.class).toInstance(plugin);
    }

    @Provides
    @Singleton
    CoreConfig config() {
        return ConfigLoader.load(configPath(), CoreModules.ALL);
    }

    @Provides
    @Singleton
    CoreServices coreServices(CoreConfig config, ExecutorService executor) {
        return CoreServices.start(config, dataDirectory(), executor);
    }

    @Provides
    @Singleton
    ModuleState moduleState(CoreConfig config) {
        return ModuleState.compute(config, name -> plugin.getServer().getPluginManager().getPlugin(name) != null);
    }

    @Provides
    @Singleton
    MenuSettings menuSettings(CoreConfig config) {
        return MenuSettings.load(configPath());
    }

    @Provides
    Branding branding(CoreConfig config) {
        return config.branding();
    }

    @Provides
    LocaleService localeService(CoreServices services) {
        return services.locale();
    }

    @Provides
    ProfileRepository profileRepository(CoreServices services) {
        return services.repository();
    }

    @Provides
    ProfileService profileService(CoreServices services) {
        return services.profiles();
    }

    @Provides
    EconomyService economyService(CoreServices services) {
        return services.economy();
    }

    @Provides
    MessageBus messageBus(CoreServices services) {
        return services.messageBus();
    }

    @Provides
    EventBus eventBus(CoreServices services) {
        return services.events();
    }

    @Provides
    TextFormatter textFormatter(CoreServices services) {
        return services.formatter();
    }

    @Provides
    @Singleton
    ServerDirectory serverDirectory() {
        return new YamlServerDirectory(dataDirectory().resolve("servers.yml"));
    }

    @Provides
    @Singleton
    NetworkStatusService networkStatus(ServerDirectory directory, MessageBus messageBus, ExecutorService executor) {
        return new NetworkStatusService(directory, messageBus, executor);
    }

    @Provides
    @Singleton
    GameStateSettings gameStateSettings(CoreConfig config) {
        return GameStateSettings.load(configPath());
    }

    @Provides
    @Singleton
    VanishSettings vanishSettings(CoreConfig config) {
        return VanishSettings.load(configPath());
    }

    @Provides
    @Singleton
    SimpleGameStateService gameStateService() {
        return new SimpleGameStateService();
    }

    @Provides
    @Singleton
    NetworkConnector networkConnector(ServerDirectory directory, NetworkStatusService status) {
        return new SelectingConnector(directory, status, new PaperConnectorTransport(plugin));
    }

    @Provides
    @Singleton
    Prismarin prismarin(CoreServices services, ServerDirectory directory, NetworkStatusService status,
                        SimpleGameStateService gameState, NetworkConnector connector) {
        return services.prismarin(directory, status, gameState, connector);
    }

    @Provides
    MiniMessage miniMessage(CoreServices services) {
        return services.formatter().miniMessage();
    }

    @Provides
    @Singleton
    ExecutorService executorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Provides
    @Singleton
    RankFormatProvider rankFormatProvider(LocaleService localeService, MiniMessage miniMessage,
                                          PermissionSnapshots snapshots, ProfileService profiles) {
        DefaultRankFormatProvider fallback = new DefaultRankFormatProvider(localeService, snapshots, profiles);
        if (plugin.getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            return new LuckPermsRankFormatProvider(miniMessage, fallback);
        }
        return fallback;
    }

    private Path dataDirectory() {
        return plugin.getDataFolder().toPath();
    }

    private Path configPath() {
        return dataDirectory().resolve("config.yml");
    }
}
