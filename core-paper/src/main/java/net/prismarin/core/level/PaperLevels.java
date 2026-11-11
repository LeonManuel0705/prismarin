package net.prismarin.core.level;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Optional;
import net.prismarin.api.PrismarinSystems;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.profile.ProfileService;
import net.prismarin.shared.config.CoreConfig;
import net.prismarin.shared.level.ProfileLevelService;

@Singleton
public final class PaperLevels {

    private final LevelService service;

    @Inject
    public PaperLevels(ProfileService profiles, CoreConfig config) {
        this.service = config.systemEnabled(PrismarinSystems.LEVELS) ? new ProfileLevelService(profiles) : null;
    }

    public Optional<LevelService> service() {
        return Optional.ofNullable(service);
    }
}
