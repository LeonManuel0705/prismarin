package net.prismarin.api;

public final class PrismarinProvider {

    private static volatile Prismarin instance;

    private PrismarinProvider() {
        throw new AssertionError();
    }

    public static Prismarin get() {
        Prismarin current = instance;
        if (current == null) {
            throw new IllegalStateException(
                    "PRISMARIN is not enabled. Declare PrismarinCore as a dependency of your plugin.");
        }
        return current;
    }

    public static boolean isAvailable() {
        return instance != null;
    }

    public static void register(Prismarin prismarin) {
        instance = prismarin;
    }

    public static void unregister() {
        instance = null;
    }
}
