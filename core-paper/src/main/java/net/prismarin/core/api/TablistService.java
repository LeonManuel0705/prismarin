package net.prismarin.core.api;

import net.prismarin.api.event.Subscription;

public interface TablistService {

    Subscription register(TablistContributor contributor, int priority);

    default Subscription register(TablistContributor contributor) {
        return register(contributor, 0);
    }

    void refresh();
}
