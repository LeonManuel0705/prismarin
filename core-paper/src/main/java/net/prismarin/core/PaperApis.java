package net.prismarin.core;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import net.prismarin.api.level.LevelApi;
import net.prismarin.api.level.LevelService;
import net.prismarin.api.staff.StaffModeApi;
import net.prismarin.api.staff.StaffModeService;
import net.prismarin.api.vanish.VanishApi;
import net.prismarin.api.vanish.VanishService;
import net.prismarin.core.api.ChatService;
import net.prismarin.core.api.SidebarService;
import net.prismarin.core.api.TablistService;
import net.prismarin.core.chat.ChatDecorations;
import net.prismarin.core.gui.MenuService;
import net.prismarin.core.gui.MenuStyle;
import net.prismarin.core.level.PaperLevels;
import net.prismarin.core.scoreboard.SidebarManager;
import net.prismarin.core.staff.PaperStaffModeService;
import net.prismarin.core.tablist.TablistManager;
import net.prismarin.core.vanish.PaperVanishService;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

@Singleton
public final class PaperApis {

    private final PrismarinCorePlugin plugin;
    private final ModuleState modules;
    private final PaperLevels levels;
    private final Provider<PaperVanishService> vanish;
    private final Provider<PaperStaffModeService> staffMode;
    private final Provider<SidebarManager> sidebar;
    private final Provider<TablistManager> tablist;
    private final Provider<ChatDecorations> chat;
    private final MenuService menus;
    private final MenuStyle menuStyle;

    @Inject
    public PaperApis(PrismarinCorePlugin plugin, ModuleState modules, PaperLevels levels,
                     Provider<PaperVanishService> vanish, Provider<PaperStaffModeService> staffMode,
                     Provider<SidebarManager> sidebar, Provider<TablistManager> tablist,
                     Provider<ChatDecorations> chat, MenuService menus, MenuStyle menuStyle) {
        this.plugin = plugin;
        this.modules = modules;
        this.levels = levels;
        this.vanish = vanish;
        this.staffMode = staffMode;
        this.sidebar = sidebar;
        this.tablist = tablist;
        this.chat = chat;
        this.menus = menus;
        this.menuStyle = menuStyle;
    }

    public void register() {
        ServicesManager services = plugin.getServer().getServicesManager();
        levels.service().ifPresent(service -> {
            LevelApi.register(service);
            services.register(LevelService.class, service, plugin, ServicePriority.Normal);
        });
        if (modules.enabled(CoreModules.VANISH)) {
            VanishService service = vanish.get();
            VanishApi.register(service);
            services.register(VanishService.class, service, plugin, ServicePriority.Normal);
        }
        if (modules.enabled(CoreModules.STAFF_MODE)) {
            StaffModeService service = staffMode.get();
            StaffModeApi.register(service);
            services.register(StaffModeService.class, service, plugin, ServicePriority.Normal);
        }
        if (modules.enabled(CoreModules.SIDEBAR)) {
            services.register(SidebarService.class, sidebar.get(), plugin, ServicePriority.Normal);
        }
        if (modules.enabled(CoreModules.TABLIST)) {
            services.register(TablistService.class, tablist.get(), plugin, ServicePriority.Normal);
        }
        if (modules.enabled(CoreModules.CHAT)) {
            services.register(ChatService.class, chat.get(), plugin, ServicePriority.Normal);
        }
        services.register(MenuService.class, menus, plugin, ServicePriority.Normal);
        services.register(MenuStyle.class, menuStyle, plugin, ServicePriority.Normal);
    }

    public static void unregister() {
        LevelApi.unregister();
        VanishApi.unregister();
        StaffModeApi.unregister();
    }
}
