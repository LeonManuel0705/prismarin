package net.prismarin.shared.reward;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import net.prismarin.api.reward.Reward;

public record VoteRules(Reward standard, Map<String, Reward> services, Map<Integer, Reward> milestones) {

    public VoteRules {
        standard = standard == null ? Reward.EMPTY : standard;
        Map<String, Reward> byService = new LinkedHashMap<>();
        services.forEach((service, reward) -> byService.put(key(service), reward));
        services = Map.copyOf(byService);
        milestones = Map.copyOf(milestones);
    }

    public Reward reward(String service, VoteLedger.Counted counted) {
        Reward base = services.getOrDefault(key(service), standard);
        if (counted == null || !counted.newDay()) {
            return base;
        }
        Reward bonus = milestones.get(counted.streak());
        return bonus == null ? base : base.plus(bonus);
    }

    public boolean milestone(VoteLedger.Counted counted) {
        return counted != null && counted.newDay() && milestones.containsKey(counted.streak());
    }

    private static String key(String service) {
        return service == null ? "" : service.strip().toLowerCase(Locale.ROOT);
    }
}
