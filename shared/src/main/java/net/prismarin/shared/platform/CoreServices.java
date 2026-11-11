package net.prismarin.shared.platform;

import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import net.prismarin.api.Prismarin;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.moderation.PunishmentService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.GameStateService;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.api.storage.DataStorage;
import net.prismarin.shared.admin.AuditLog;
import net.prismarin.shared.admin.LocalAuditService;
import net.prismarin.shared.admin.ReportService;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.economy.DisabledEconomyService;
import net.prismarin.shared.economy.EconomyServiceImpl;
import net.prismarin.shared.event.SimpleEventBus;
import net.prismarin.shared.locale.BrandingTags;
import net.prismarin.shared.locale.YamlLocaleService;
import net.prismarin.shared.messaging.MessageBusFactory;
import net.prismarin.shared.moderation.DocumentPunishmentService;
import net.prismarin.shared.profile.ProfileServiceImpl;
import net.prismarin.shared.storage.DocumentStore;
import net.prismarin.shared.storage.PluginDataStorage;
import net.prismarin.shared.storage.ProfileRepository;
import net.prismarin.shared.storage.ProfileStorage;
import net.prismarin.shared.text.TextFormatter;

public final class CoreServices implements AutoCloseable {

    private final CoreConfig config;
    private final ProfileStorage storage;
    private final EventBus events;
    private final TextFormatter formatter;
    private final LocaleService locale;
    private final ProfileServiceImpl profiles;
    private final EconomyService economy;
    private final MessageBus messageBus;
    private final PunishmentService punishments;
    private final ReportService reports;
    private final AuditLog audit;
    private final AuditService auditService;
    private final DataStorage dataStorage;

    private CoreServices(CoreConfig config, Path dataDirectory, ExecutorService executor) {
        this.config = config;
        this.storage = ProfileStorage.create(config, dataDirectory);
        this.events = new SimpleEventBus();
        this.formatter = BrandingTags.formatter(config.branding());
        this.locale = new YamlLocaleService(dataDirectory, config.defaultLocale(), formatter);
        ProfileRepository repository = storage.repository();
        DocumentStore documents = storage.documents();
        this.profiles = new ProfileServiceImpl(repository, config.defaultLocale(), executor, events, documents);
        this.economy = config.systemEnabled(PrismarinSystems.ECONOMY)
                ? new EconomyServiceImpl(repository, executor, config.currencies(), config.primaryCurrency(), events)
                : new DisabledEconomyService();
        this.messageBus = MessageBusFactory.create(config);
        this.punishments = new DocumentPunishmentService(documents, executor, events, Clock.systemUTC());
        this.reports = new ReportService(documents, executor, Clock.systemUTC());
        this.audit = new AuditLog(documents, executor, Clock.systemUTC());
        this.auditService = new LocalAuditService(audit);
        this.dataStorage = new PluginDataStorage(documents, executor);
    }

    public static CoreServices start(CoreConfig config, Path dataDirectory, ExecutorService executor) {
        return new CoreServices(config, dataDirectory, executor);
    }

    public Prismarin prismarin(ServerDirectory servers, ServerStatusService serverStatus, GameStateService gameState,
                               NetworkConnector connector) {
        return new SimplePrismarin(config.serverId(), config.branding(), profiles, economy, locale, messageBus,
                servers, serverStatus, gameState, connector, punishments, events, enabledSystems());
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

    public PunishmentService punishments() {
        return punishments;
    }

    public ReportService reports() {
        return reports;
    }

    public AuditLog audit() {
        return audit;
    }

    public AuditService auditService() {
        return auditService;
    }

    public DataStorage dataStorage() {
        return dataStorage;
    }

    public DocumentStore documents() {
        return storage.documents();
    }

    public void rememberName(UUID uuid, String name) {
        profiles.rememberName(uuid, name);
    }

    @Override
    public void close() {
        messageBus.close();
        storage.close();
    }
}
