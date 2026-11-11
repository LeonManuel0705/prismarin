package net.prismarin.api.admin;

public enum Delivery {
    DELIVERED,
    NO_ROUTE;

    public boolean delivered() {
        return this == DELIVERED;
    }
}
