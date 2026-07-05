package net.prismarin.core;

import com.google.inject.Injector;

/**
 * Entry point for plugins depending on PrismarinCore.
 * This is the single allowed service locator on Paper servers: dependent
 * plugins fetch the injector here instead of holding their own references
 * to core internals. The instance is set while PrismarinCore is enabled
 * and cleared again on disable.
 */
public final class CoreApi {

    private static volatile CoreApi instance;

    private final Injector injector;

    private CoreApi(Injector injector) {
        this.injector = injector;
    }

    /**
     * Returns the API instance of the running PrismarinCore plugin.
     *
     * @return the active API instance
     * @throws IllegalStateException if PrismarinCore is not enabled
     */
    public static CoreApi get() {
        CoreApi api = instance;
        if (api == null) {
            throw new IllegalStateException("PrismarinCore is not enabled");
        }
        return api;
    }

    /**
     * Returns the Guice injector holding all core service bindings.
     *
     * @return the core injector
     */
    public Injector injector() {
        return injector;
    }

    /**
     * Convenience shortcut resolving a service from the core injector.
     *
     * @param type the bound service type
     * @param <T> the service type
     * @return the singleton service instance
     */
    public <T> T service(Class<T> type) {
        return injector.getInstance(type);
    }

    static void register(Injector injector) {
        instance = new CoreApi(injector);
    }

    static void unregister() {
        instance = null;
    }
}
