package com.mine.geometry_node.core.engine.spatial;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Spatial-anchor adapter for the existing extensible dynamic-visual payload. */
public final class VisualSpatialAnchors {
    private static final SpatialAnchorCodec CODEC = new SpatialAnchorCodec();

    private VisualSpatialAnchors() {}

    /** Visual packets are scoped to their execution dimension. */
    public static boolean canBroadcast(@Nullable SpatialAnchor anchor, @Nullable Level level) {
        return anchor != null && (anchor.dimension() == null || level == null
                || anchor.dimension().equals(level.dimension()));
    }

    public static void write(CompoundTag data, String key, SpatialAnchor anchor,
                             @Nullable Level level) {
        java.util.Objects.requireNonNull(anchor, "anchor");
        SpatialAnchor bound = anchor.dimension() == null && level != null
                ? anchor.withDimension(level.dimension()) : anchor;
        data.put(key, CODEC.serialize(bound));
    }

    public static SpatialAnchor read(CompoundTag data, String key) {
        if (!data.contains(key)) throw new IllegalArgumentException("Missing visual spatial anchor: " + key);
        return CODEC.deserialize(data.get(key));
    }

    public static @Nullable Vec3 worldPosition(SpatialAnchor anchor, Level level, SpatialTransform transform) {
        Vec3 base=SpatialAnchorResolver.resolve(anchor,level);
        var rotation=SpatialAnchorResolver.resolveRotation(anchor,level);
        return base==null||rotation==null?null:transform.position(base,rotation);
    }

    /** Evaluates the initial visibility center without folding it into the live offset. */
    public static @Nullable Vec3 worldPosition(SpatialAnchor anchor, Level level, Vec3 offset) {
        Vec3 base = SpatialAnchorResolver.resolve(anchor, level);
        return base == null ? null : base.add(offset);
    }
}
