package net.prismarin.api.admin;

import java.util.List;
import net.prismarin.api.event.Subscription;

public interface ReloadService {

    Subscription register(String name, Reloadable part);

    List<String> parts();

    ReloadResult reloadAll();
}
