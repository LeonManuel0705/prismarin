package net.prismarin.core.remote;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.time.Clock;
import net.prismarin.api.admin.AdminApi;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.admin.NotificationService;
import net.prismarin.api.moderation.AltService;
import net.prismarin.api.moderation.ModerationApi;
import net.prismarin.api.moderation.PunishmentTemplates;
import net.prismarin.api.moderation.ReportService;
import net.prismarin.api.nick.NickApi;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.storage.DataStorage;
import net.prismarin.api.storage.StorageApi;
import net.prismarin.core.PrismarinCorePlugin;
import net.prismarin.core.social.BackendFriendService;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.network.ModerationCatalog;
import net.prismarin.shared.network.RemoteAlts;
import net.prismarin.shared.network.RemoteAudit;
import net.prismarin.shared.network.RemoteMaintenance;
import net.prismarin.shared.network.RemoteNotifications;
import net.prismarin.shared.network.RemoteReports;
import net.prismarin.shared.network.RemoteTemplates;
import net.prismarin.shared.platform.CoreServices;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

@Singleton
public final class BackendApis implements Listener {

    private static final long CATALOG_DELAY_TICKS = 40L;

    private final PrismarinCorePlugin plugin;
    private final CoreServices services;
    private final CoreConfig config;
    private final PaperProxyLink link;
    private ModerationCatalog catalog;

    @Inject
    public BackendApis(PrismarinCorePlugin plugin, CoreServices services, CoreConfig config, PaperProxyLink link) {
        this.plugin = plugin;
        this.services = services;
        this.config = config;
        this.link = link;
    }

    public void register(NickService nicks) {
        link.start();
        catalog = new ModerationCatalog(link, Clock.systemUTC());
        DataStorage storage = services.dataStorage();
        AuditService audit = new RemoteAudit(services.auditService(), link,
                BackendFriendService.sharedStorage(config.storageMode()));
        MaintenanceService maintenance = new RemoteMaintenance(link);
        NotificationService notifications = new RemoteNotifications(link);
        ReportService reports = new RemoteReports(link, catalog);
        PunishmentTemplates templates = new RemoteTemplates(link, catalog);
        AltService alts = new RemoteAlts(link);
        StorageApi.register(storage);
        AdminApi.register(audit, maintenance, notifications);
        ModerationApi.registerReports(reports);
        ModerationApi.registerTemplates(templates);
        ModerationApi.registerAlts(alts);
        ServicesManager manager = plugin.getServer().getServicesManager();
        manager.register(DataStorage.class, storage, plugin, ServicePriority.Normal);
        manager.register(AuditService.class, audit, plugin, ServicePriority.Normal);
        manager.register(MaintenanceService.class, maintenance, plugin, ServicePriority.Normal);
        manager.register(NotificationService.class, notifications, plugin, ServicePriority.Normal);
        manager.register(ReportService.class, reports, plugin, ServicePriority.Normal);
        manager.register(PunishmentTemplates.class, templates, plugin, ServicePriority.Normal);
        manager.register(AltService.class, alts, plugin, ServicePriority.Normal);
        if (nicks != null) {
            NickApi.register(nicks);
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void unregister() {
        NickApi.unregister();
        ModerationApi.unregister();
        AdminApi.unregister();
        StorageApi.unregister();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        ModerationCatalog current = catalog;
        if (current != null && current.stale()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, current::refreshWhenStale, CATALOG_DELAY_TICKS);
        }
    }
}
