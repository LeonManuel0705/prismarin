package net.prismarin.velocity.joinme;

import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import net.prismarin.api.admin.PlatformApi;
import net.prismarin.api.joinme.JoinMeApi;
import net.prismarin.api.player.PlayerService;
import net.prismarin.api.skin.SkinFaceApi;
import net.prismarin.shared.skin.SkinFaceService;
import net.prismarin.velocity.admin.AdminContext;
import org.slf4j.Logger;

public final class JoinMeModule {

    private JoinMeModule() {
        throw new AssertionError();
    }

    public static ProxyJoinMeService register(Object plugin, Logger logger, AdminContext context,
                                              PlayerService players, Executor executor, Path dataDirectory) {
        Path file = dataDirectory.resolve(JoinMeSettings.FILE_NAME);
        JoinMeSettings settings = JoinMeSettings.load(file);
        settings.problems().forEach(problem -> logger.warn("{}: {}", JoinMeSettings.FILE_NAME, problem));
        ProxyServer proxy = context.proxy();
        SkinFaceService faces = SkinFaceService.create(uuid -> CompletableFuture.completedFuture(
                        proxy.getPlayer(uuid).map(player -> ProxyJoinMeService.textures(players, player))
                                .filter(value -> !value.isEmpty())),
                executor, dataDirectory.resolve(SkinFaceService.FOLDER), settings.face().cache());
        ProxyJoinMeService service = new ProxyJoinMeService(context, players, settings, faces, executor, logger);
        SkinFaceApi.register(faces);
        proxy.getEventManager().register(plugin, service);
        service.start();
        JoinMeApi.register(service);
        PlatformApi.reloads().ifPresent(reloads -> reloads.register("joinme", () -> {
            JoinMeSettings fresh = JoinMeSettings.load(file);
            fresh.problems().forEach(problem -> logger.warn("{}: {}", JoinMeSettings.FILE_NAME, problem));
            service.reload(fresh);
        }));
        proxy.getEventManager().register(plugin, ProxyShutdownEvent.class, event -> {
            JoinMeApi.unregister();
            SkinFaceApi.unregister();
        });
        proxy.getScheduler().buildTask(plugin, service::sweep).delay(1, TimeUnit.SECONDS)
                .repeat(1, TimeUnit.SECONDS).schedule();
        logger.info("JoinMe on: audience {} {}, same server {}, {} cooldown tiers",
                settings.audience().audience().scope().id(), settings.audience().audience().groups(),
                settings.join().sameServer(), settings.cooldowns().values().size());
        return service;
    }
}
