package net.prismarin.api.social;

import java.util.Objects;
import java.util.Optional;

public record ClanResult(SocialResult result, Optional<Clan> clan) {

    public ClanResult {
        Objects.requireNonNull(result, "result");
        clan = clan == null ? Optional.empty() : clan;
    }

    public static ClanResult of(SocialResult result) {
        return new ClanResult(result, Optional.empty());
    }

    public static ClanResult of(SocialResult result, Clan clan) {
        return new ClanResult(result, Optional.ofNullable(clan));
    }

    public boolean success() {
        return result.success();
    }
}
