package net.prismarin.core;

import com.google.inject.Injector;
import java.util.Optional;
import net.prismarin.core.api.ChatService;
import net.prismarin.core.api.SidebarService;
import net.prismarin.core.api.TablistService;
import net.prismarin.core.chat.ChatDecorations;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.gui.MenuStyle;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.core.tablist.TablistManager;

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

    public static boolean isAvailable() {
        return instance != null;
    }

    public Injector injector() {
        return injector;
    }

    public <T> T service(Class<T> type) {
        return injector.getInstance(type);
    }

    public Optional<SidebarService> sidebar() {
        return enabled(CoreModules.SIDEBAR) ? Optional.of(service(SidebarManager.class)) : Optional.empty();
    }

    public Optional<TablistService> tablist() {
        return enabled(CoreModules.TABLIST) ? Optional.of(service(TablistManager.class)) : Optional.empty();
    }

    public Optional<ChatService> chat() {
        return enabled(CoreModules.CHAT) ? Optional.of(service(ChatDecorations.class)) : Optional.empty();
    }

    public MenuService menus() {
        return service(MenuService.class);
    }

    public MenuStyle menuStyle() {
        return service(MenuStyle.class);
    }

    private boolean enabled(String module) {
        return service(ModuleState.class).enabled(module);
    }

    static void register(Injector injector) {
        instance = new CoreApi(injector);
    }

    static void unregister() {
        instance = null;
    }
}
