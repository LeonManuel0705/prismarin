package net.prismarin.core;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.core.config.MenuSettings;
import net.prismarin.core.rank.DefaultRankFormatProvider;
import net.prismarin.core.rank.LuckPermsRankFormatProvider;
import net.prismarin.core.rank.RankFormatProvider;
import net.prismarin.core.server.YamlServerDirectory;
import net.prismarin.core.storage.ProfileStorage;
import net.prismarin.shared.config.ConfigLoader;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.economy.EconomyServiceImpl;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.messaging.MessageBusFactory;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.storage.ProfileRepository;

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
        return ConfigLoader.load(configPath());
    }

    @Provides
    @Singleton
    MenuSettings menuSettings(CoreConfig config) {
        return MenuSettings.load(configPath());
    }

    @Provides
    @Singleton
    LocaleService localeService() {
        return new YamlLocaleService(dataDirectory());
    }

    @Provides
    @Singleton
    ProfileStorage profileStorage(CoreConfig config) {
        return ProfileStorage.create(config, dataDirectory());
    }

    @Provides
    @Singleton
    ProfileRepository profileRepository(ProfileStorage storage) {
        return storage.repository();
    }

    @Provides
    @Singleton
    ProfileService profileService(ProfileRepository repository, CoreConfig config) {
        return new ProfileServiceImpl(repository, config.defaultLocale());
    }

    @Provides
    @Singleton
    EconomyService economyService(ProfileRepository repository) {
        return new EconomyServiceImpl(repository);
    }

    @Provides
    @Singleton
    MessageBus messageBus(CoreConfig config) {
        return MessageBusFactory.create(config);
    }

    @Provides
    @Singleton
    ServerDirectory serverDirectory() {
        return new YamlServerDirectory(dataDirectory().resolve("servers.yml"));
    }

    @Provides
    @Singleton
    MiniMessage miniMessage() {
        return MiniMessage.miniMessage();
    }

    @Provides
    @Singleton
    ExecutorService executorService() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Provides
    @Singleton
    RankFormatProvider rankFormatProvider(LocaleService localeService, MiniMessage miniMessage) {
        DefaultRankFormatProvider fallback = new DefaultRankFormatProvider(localeService);
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
