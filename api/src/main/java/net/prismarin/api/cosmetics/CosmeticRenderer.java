package net.prismarin.api.cosmetics;

import java.util.List;

@FunctionalInterface
public interface CosmeticRenderer {

    List<CosmeticPoint> render(CosmeticFrame frame);
}
