package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.components.menu.UiGridMenu;
import com.mine.geometry_node.client.ui.components.valuepreview.MarkerTypePreview;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import com.mine.geometry_node.core.node.definition.port.PortOptionResolver;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/** Client presentation adapters keyed by option registry; values and commits stay in the SELECT renderer. */
public final class SelectMenuPresentations {
    @FunctionalInterface
    public interface Presentation {
        void show(Context context, String title, PortOptionResolver.Resolution options,
                  String selected, View anchor, FrameLayout host, Consumer<String> onSelect);
    }
    private static final Map<String, Presentation> PRESENTATIONS = new ConcurrentHashMap<>();
    static {
        register(MarkerTypeRegistry.DYNAMIC_REGISTRY_ID, (context, title, options, selected, anchor, host, onSelect) -> {
            var entries = options.options().stream().map(option -> new UiGridMenu.Entry<>(
                    option.id(), option.label(), option.id(),
                    (java.util.function.Function<Context, View>) c -> new MarkerTypePreview(c, option.id()), true)).toList();
            new UiGridMenu<>(context, title, entries, selected, onSelect).showBelow(anchor, host);
        });
    }
    private SelectMenuPresentations() {}
    public static void register(String registryId, Presentation presentation) {
        if (registryId == null || registryId.isBlank() || presentation == null)
            throw new IllegalArgumentException("A registry and presentation are required");
        if (PRESENTATIONS.putIfAbsent(registryId, presentation) != null)
            throw new IllegalStateException("Duplicate select presentation: " + registryId);
    }
    public static Presentation get(String registryId) { return PRESENTATIONS.get(registryId); }
}
