package com.mine.geometry_node.client.runtime.game.selection;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;
import java.util.Objects;

/** Immutable, typed identity and geometry. No live entity/world references cross into the UI. */
public record ViewportTarget(Key key, AABB bounds, Decoration decoration, List<Vec3> visiblePoints) {
    public record Key(String source, String id) {
        public Key { Objects.requireNonNull(source); Objects.requireNonNull(id); }
    }
    public enum Decoration { DISC, BOX }
    public ViewportTarget { visiblePoints = List.copyOf(visiblePoints); }
}
