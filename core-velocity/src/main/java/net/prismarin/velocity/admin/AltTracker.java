package net.prismarin.velocity.admin;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.ResultedEvent;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.prismarin.api.moderation.AltAccount;
import net.prismarin.api.moderation.BannedAltJoinEvent;
import net.prismarin.api.moderation.Punishment;
import net.prismarin.api.moderation.PunishmentIssuedEvent;
import net.prismarin.api.moderation.PunishmentType;
import net.prismarin.shared.moderation.DocumentAltService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AltTracker {

    public static final String STAFF = "prismarin.staff.alts";
    public static final String BYPASS = "prismarin.alts.bypass";

    private static final Logger LOGGER = LoggerFactory.getLogger("PrismarinCore");

    private final AdminContext context;
    private final AltSettings settings;
    private final DocumentAltService alts;
    private final AddressKeys addresses;
    private final PunishmentFlow punishments;

    public AltTracker(AdminContext context, AltSettings settings, DocumentAltService alts, AddressKeys addresses,
                      PunishmentFlow punishments) {
        this.context = context;
        this.settings = settings;
        this.alts = alts;
        this.addresses = addresses;
        this.punishments = punishments;
        settings.problems().forEach(problem -> LOGGER.warn("{}: {}", AltSettings.FILE_NAME, problem));
        if (enforcing()) {
            context.services().events().subscribe(PunishmentIssuedEvent.class, event -> banned(event.punishment()));
        }
    }

    public void prune() {
        alts.prune().thenAccept(removed -> {
            if (removed > 0) {
                LOGGER.info("Removed {} expired alt records", removed);
            }
        }).exceptionally(error -> {
            LOGGER.warn("Removing expired alt records failed", error);
            return null;
        });
    }

    @Subscribe(order = PostOrder.LATE)
    public EventTask onLogin(LoginEvent event) {
        Player player = event.getPlayer();
        Optional<String> hash = addresses.of(player.getRemoteAddress());
        if (hash.isEmpty()) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        CompletableFuture<Void> recorded = alts.record(player.getUniqueId(), player.getUsername(), hash.get())
                .exceptionally(error -> {
                    LOGGER.warn("Storing the address hash of {} failed", player.getUsername(), error);
                    return null;
                });
        if (!event.getResult().isAllowed() || player.hasPermission(BYPASS)
                || !settings.alertStaff() && !enforcing()) {
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }
        CompletableFuture<Void> checked = recorded.thenCompose(done -> alts.bannedAlts(player.getUniqueId()))
                .thenCompose(banned -> banned.isEmpty() ? CompletableFuture.<Void>completedFuture(null)
                        : react(event, player, banned))
                .exceptionally(error -> {
                    LOGGER.warn("Checking the alts of {} failed", player.getUsername(), error);
                    return null;
                });
        return EventTask.resumeWhenComplete(enforcing() ? checked : CompletableFuture.completedFuture(null));
    }

    private CompletableFuture<Void> react(LoginEvent event, Player player, List<AltAccount> banned) {
        if (!enforcing()) {
            announce(player, banned, false);
            return CompletableFuture.completedFuture(null);
        }
        return context.services().punishments().active(banned.getFirst().uuid(), PunishmentType.BAN)
                .thenAccept(ban -> {
                    boolean denied = ban.isPresent() && event.getResult().isAllowed();
                    if (denied) {
                        event.setResult(ResultedEvent.ComponentResult.denied(punishments.screen(player, ban.get())));
                    }
                    announce(player, banned, denied);
                });
    }

    private void announce(Player player, List<AltAccount> banned, boolean denied) {
        if (settings.alertStaff()) {
            for (Player staff : context.proxy().getAllPlayers()) {
                if (staff.hasPermission(STAFF) && !staff.getUniqueId().equals(player.getUniqueId())) {
                    staff.sendMessage(alert(staff, player.getUsername(), banned, denied));
                }
            }
            context.proxy().getConsoleCommandSource().sendMessage(alert(context.proxy().getConsoleCommandSource(),
                    player.getUsername(), banned, denied));
        }
        context.services().audit().record("Alts", denied ? "alts.denied" : "alts.banned-alt", player.getUsername(),
                String.join(", ", banned.stream().map(AltCommands::name).toList()));
        try {
            context.services().events().publish(new BannedAltJoinEvent(player.getUniqueId(), player.getUsername(),
                    banned, denied));
        } catch (RuntimeException error) {
            LOGGER.warn("A BannedAltJoinEvent listener failed", error);
        }
    }

    private Component alert(CommandSource viewer, String name,
                            List<AltAccount> banned, boolean denied) {
        return context.messages().render(viewer, denied ? "alts.join-denied" : "alts.join-alert",
                        Placeholder.unparsed("player", name),
                        Placeholder.component("alts", AltCommands.names(context, viewer, banned)))
                .hoverEvent(HoverEvent.showText(context.messages().render(viewer, "alts.join-hover",
                        Placeholder.unparsed("player", name))))
                .clickEvent(ClickEvent.runCommand("/alts " + name));
    }

    private void banned(Punishment punishment) {
        if (punishment.type() != PunishmentType.BAN || !punishment.inForce(System.currentTimeMillis())) {
            return;
        }
        alts.alts(punishment.target()).thenAccept(found -> {
            for (AltAccount alt : found) {
                context.proxy().getPlayer(alt.uuid())
                        .filter(player -> !player.hasPermission(BYPASS))
                        .ifPresent(player -> {
                            player.disconnect(punishments.screen(player, punishment));
                            context.notifyStaff(STAFF, "alts.removed", Placeholder.unparsed("player",
                                    player.getUsername()), Placeholder.unparsed("target", punishment.targetName()));
                        });
            }
        }).exceptionally(error -> {
            LOGGER.warn("Removing the alts of {} failed", punishment.targetName(), error);
            return null;
        });
    }

    private boolean enforcing() {
        return settings.enforceBans() && punishments != null;
    }
}
