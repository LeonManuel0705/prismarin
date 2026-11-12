package net.prismarin.api.reward;

@FunctionalInterface
public interface RewardHandler {

    void give(RewardContext context, String value);
}
