package net.prismarin.api.event;

@FunctionalInterface
public interface Subscription {

    void unsubscribe();
}
