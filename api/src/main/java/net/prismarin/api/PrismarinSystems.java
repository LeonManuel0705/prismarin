package net.prismarin.api;

import java.util.List;

public final class PrismarinSystems {

    public static final String ECONOMY = "economy";
    public static final String LEVELS = "levels";
    public static final String NETWORK_STATUS = "network-status";

    public static final List<String> ALL = List.of(ECONOMY, LEVELS, NETWORK_STATUS);

    private PrismarinSystems() {
        throw new AssertionError();
    }
}
