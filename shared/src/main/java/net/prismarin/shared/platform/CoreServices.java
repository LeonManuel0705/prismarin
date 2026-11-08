package net.prismarin.shared.platform;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.economy.BalanceChangedEvent;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.economy.DisabledEconomyService;
import net.prismarin.shared.economy.EconomyServiceImpl;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.locale.BrandingTags;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.messaging.MessageBusFactory;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.storage.ProfileRepository;
import net.prismarin.shared.storage.ProfileStorage;
import net.prismarin.shared.text.TextFormatter;

public final class CoreServices implements AutoCloseable {

    private final CoreConfig config;
    private final ProfileStorage storage;
    private final EventBus events;
    private final TextFormatter formatter;
    private final LocaleService locale;
    private final ProfileService profiles;
    private final EconomyService economy;
    private final MessageBus messageBus;

    private CoreServices(CoreConfig config, Path dataDirectory, ExecutorService executor) {
        this.config = config;
        this.storage = ProfileStorage.create(config, dataDirectory);
        this.events = new SimpleEventBus();
        this.formatter = BrandingTags.formatter(config.branding());
        this.locale = new YamlLocaleService(dataDirectory, config.defaultLocale(), formatter);
        ProfileRepository repository = storage.repository();
        this.profiles = new ProfileServiceImpl(repository, config.defaultLocale(), executor, events);
        this.economy = config.systemEnabled(PrismarinSystems.ECONOMY)
                ? new EconomyServiceImpl(repository, executor, config.currencies(), config.primaryCurrency(), events)
                : new DisabledEconomyService();
        this.messageBus = MessageBusFactory.create(config);
        events.subscribe(BalanceChangedEvent.class, event -> profiles.getCached(event.uuid())
                .ifPresent(profile -> profile.balance(event.currency(), event.newBalance())));
    }

    public static CoreServices start(CoreConfig config, Path dataDirectory, ExecutorService executor) {
        return new CoreServices(config, dataDirectory, executor);
    }

    public Prismarin prismarin(ServerDirectory servers, ServerStatusService serverStatus) {
        return new SimplePrismarin(config.serverId(), config.branding(), profiles, economy, locale, messageBus,
                servers, serverStatus, events, enabledSystems());
    }

    public Set<String> enabledSystems() {
        return PrismarinSystems.ALL.stream().filter(config::systemEnabled).collect(Collectors.toUnmodifiableSet());
    }

    public CoreConfig config() {
        return config;
    }

    public ProfileRepository repository() {
        return storage.repository();
    }

    public EventBus events() {
        return events;
    }

    public TextFormatter formatter() {
        return formatter;
    }

    public LocaleService locale() {
        return locale;
    }

    public ProfileService profiles() {
        return profiles;
    }

    public EconomyService economy() {
        return economy;
    }

    public MessageBus messageBus() {
        return messageBus;
    }

    @Override
    public void close() {
        messageBus.close();
        storage.close();
    }
}
