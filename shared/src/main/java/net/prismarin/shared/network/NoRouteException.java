package net.prismarin.shared.network;

public final class NoRouteException extends RuntimeException {

    public NoRouteException() {
        super("no player online on this server to carry the request and no Redis message bus");
    }
}
