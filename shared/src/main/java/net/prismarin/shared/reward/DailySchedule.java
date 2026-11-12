package net.prismarin.shared.reward;

import java.util.Map;
import java.util.TreeMap;
import net.prismarin.api.reward.Reward;

public record DailySchedule(int cycle, Map<Integer, Reward> days, Reward fallback, Map<Integer, Reward> milestones) {

    public DailySchedule {
        cycle = Math.max(1, cycle);
        days = Map.copyOf(days);
        fallback = fallback == null ? Reward.EMPTY : fallback;
        milestones = Map.copyOf(milestones);
    }

    public int day(int streak) {
        return (Math.max(1, streak) - 1) % cycle + 1;
    }

    public boolean milestone(int streak) {
        return milestones.containsKey(streak);
    }

    public Reward reward(int streak) {
        Reward base = days.getOrDefault(day(streak), fallback);
        Reward bonus = milestones.get(streak);
        return bonus == null ? base : base.plus(bonus);
    }

    public Map<Integer, Reward> sortedMilestones() {
        return new TreeMap<>(milestones);
    }
}
