package net.prismarin.api.event;

public interface Cancellable {

    boolean isCancelled();

    void setCancelled(boolean cancelled);
}
