package com.mine.geometry_node.client.runtime.game.selection;

import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/** Plugins capture on the render thread and receive immutable identities for contextual actions. */
public final class ViewportSelectionSources {
    public interface Source {
        String id();
        List<ViewportTarget> capture(PresentedViewportFrame frame);
        default boolean action(Set<String> ids, PresentedViewportFrame frame, ViewportSceneMath.Ray ray,
                               ViewportWorldActions feedback) { return false; }
    }
    private static final List<Source> SOURCES = new CopyOnWriteArrayList<>();
    static { register(new ViewportEntitySource()); }
    private ViewportSelectionSources() {}
    public static synchronized void register(Source source) {
        Objects.requireNonNull(source);
        net.minecraft.resources.Identifier.parse(source.id());
        if (source.id().equals("geometry_node:scene")) throw new IllegalArgumentException("Reserved scene selection source");
        if (SOURCES.stream().anyMatch(s -> s.id().equals(source.id())))
            throw new IllegalArgumentException("Duplicate viewport selection source: " + source.id());
        SOURCES.add(Objects.requireNonNull(source));
    }
    public static List<ViewportTarget> capture(PresentedViewportFrame frame) {
        List<ViewportTarget> result = new ArrayList<>();
        for (var source : SOURCES) for (var target : source.capture(frame)) {
            if (!target.key().source().equals(source.id())) throw new IllegalArgumentException("Selection target/source mismatch");
            result.add(target);
        }
        return List.copyOf(result);
    }
    public static boolean action(Set<ViewportTarget.Key> selected, PresentedViewportFrame frame,
                                 ViewportSceneMath.Ray ray, ViewportWorldActions feedback) {
        boolean handled = false;
        for (var source : SOURCES) {
            Set<String> ids = new LinkedHashSet<>();
            for (var key : selected) if (key.source().equals(source.id())) ids.add(key.id());
            if (!ids.isEmpty()) handled |= source.action(ids, frame, ray, feedback);
        }
        return handled;
    }
}
