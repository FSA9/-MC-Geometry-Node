package com.mine.geometry_node.client.runtime.game.snap;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Per-view settings. Enabling is independent of the retained target and base choices. */
public record ViewportSnapSettings(boolean enabled, Set<Target> targets, Base base,
                                   boolean excludeUnselectable) {
    public enum Base { CLOSEST, CENTER }
    public enum Target {
        INCREMENT, GRID, VERTEX, EDGE, FACE, VOLUME, EDGE_MIDPOINT, FACE_PROJECT, FACE_NEAREST;
        public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
    }
    public ViewportSnapSettings {
        targets = Set.copyOf(targets);
        if (targets.isEmpty()) throw new IllegalArgumentException("A snap target is required");
        Objects.requireNonNull(base);
    }
    public static ViewportSnapSettings defaults() {
        return new ViewportSnapSettings(false, Set.of(Target.GRID), Base.CENTER, true);
    }
    public ViewportSnapSettings withEnabled(boolean value) { return new ViewportSnapSettings(value, targets, base, excludeUnselectable); }
    public ViewportSnapSettings withBase(Base value) { return new ViewportSnapSettings(enabled, targets, value, excludeUnselectable); }
    public ViewportSnapSettings withExcludeUnselectable(boolean value) { return new ViewportSnapSettings(enabled, targets, base, value); }
    public ViewportSnapSettings toggle(Target target) {
        var next = EnumSet.copyOf(targets);
        if (!next.remove(target)) next.add(target);
        return next.isEmpty() ? this : new ViewportSnapSettings(enabled, next, base, excludeUnselectable);
    }
    public boolean needsGeometry() {
        return enabled && targets.stream().anyMatch(t -> t != Target.GRID && t != Target.INCREMENT);
    }
}
