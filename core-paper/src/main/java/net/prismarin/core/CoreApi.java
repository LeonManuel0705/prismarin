package net.prismarin.core;

import com.google.inject.Injector;

public final class CoreApi {

    private static volatile CoreApi instance;

    private final Injector injector;

    private CoreApi(Injector injector) {
        this.injector = injector;
    }

    public static CoreApi get() {
        CoreApi api = instance;
        if (api == null) {
            throw new IllegalStateException("PrismarinCore is not enabled");
        }
        return api;
    }

    public Injector injector() {
        return injector;
    }

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
