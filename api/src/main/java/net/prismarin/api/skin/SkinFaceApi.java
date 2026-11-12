package net.prismarin.api.skin;

import java.util.Optional;

public final class SkinFaceApi {

    private static volatile SkinFaces service;

    private SkinFaceApi() {
        throw new AssertionError();
    }

    public static Optional<SkinFaces> service() {
        return Optional.ofNullable(service);
    }

    public static void register(SkinFaces faces) {
        service = faces;
    }

    public static void unregister() {
        service = null;
    }
}
