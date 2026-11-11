package net.prismarin.api;

import java.util.Optional;
import java.util.Set;
import net.prismarin.api.admin.AdminApi;
import net.prismarin.api.admin.AuditService;
import net.prismarin.api.admin.MaintenanceService;
import net.prismarin.api.admin.NotificationService;
import net.prismarin.api.brand.Branding;
import net.prismarin.api.economy.EconomyService;
import net.prismarin.api.event.EventBus;
import net.prismarin.api.level.LevelApi;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.locale.LocaleService;
import net.prismarin.api.messaging.MessageBus;
import net.prismarin.api.moderation.ModerationApi;
import net.prismarin.api.moderation.PunishmentService;
import net.prismarin.api.moderation.PunishmentTemplates;
import net.prismarin.api.moderation.ReportService;
import net.prismarin.api.nick.NickApi;
import net.prismarin.api.nick.NickService;
import net.prismarin.api.player.PlayerApi;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.api.server.GameStateService;
import net.prismarin.api.server.NetworkConnector;
import net.prismarin.api.server.ServerDirectory;
import net.prismarin.api.server.ServerStatusService;
import net.prismarin.api.social.FriendService;
import net.prismarin.api.social.PartyService;
import net.prismarin.api.social.SocialApi;
import net.prismarin.api.staff.StaffModeApi;
import net.prismarin.api.staff.StaffModeService;
import net.prismarin.api.storage.DataStorage;
import net.prismarin.api.storage.StorageApi;
import net.prismarin.api.vanish.VanishApi;
import net.prismarin.api.vanish.VanishService;

public interface Prismarin {

    String serverId();

    Branding branding();

    ProfileService profiles();

    EconomyService economy();

    LocaleService locale();

    MessageBus messageBus();

    ServerDirectory servers();

    ServerStatusService serverStatus();

    GameStateService gameState();

    NetworkConnector connector();

    PunishmentService punishments();

    EventBus events();

    Set<String> enabledSystems();

    default boolean systemEnabled(String system) {
        return enabledSystems().contains(system);
    }

    default PlayerService players() {
        return PlayerApi.get();
    }

    default Optional<FriendService> friends() {
        return SocialApi.friends();
    }

    default Optional<PartyService> parties() {
        return SocialApi.parties();
    }

    default Optional<ReportService> reports() {
        return ModerationApi.reports();
    }

    default Optional<PunishmentTemplates> punishmentTemplates() {
        return ModerationApi.templates();
    }

    default Optional<LevelService> levels() {
        return LevelApi.service();
    }

    default Optional<VanishService> vanish() {
        return VanishApi.service();
    }

    default Optional<StaffModeService> staffMode() {
        return StaffModeApi.staffMode();
    }

    default Optional<NickService> nicks() {
        return NickApi.service();
    }

    default DataStorage storage() {
        return StorageApi.get();
    }

    default AuditService audit() {
        return AdminApi.audit().orElseThrow(AdminApi::missing);
    }

    default Optional<MaintenanceService> maintenance() {
        return AdminApi.maintenance();
    }

    default NotificationService notifications() {
        return AdminApi.notifications().orElseThrow(AdminApi::missing);
    }
}
