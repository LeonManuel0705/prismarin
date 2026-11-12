package net.prismarin.api.admin;

@FunctionalInterface
public interface Reloadable {

    void reload() throws Exception;
}
