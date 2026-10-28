package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/** Per-effect state: resolves attachment separately from independently evaluated offsets. */
record VisualAnchor(SpatialAnchor anchor, LiveValue.State<Vec3> worldPosition) {
    @Nullable Vec3 resolve(@Nullable Level level, float partialTick, ExpressionEvaluationContext context) {
        if (anchor.dimension() != null && (level == null || !anchor.dimension().equals(level.dimension()))) return null;
        if (anchor.requiresEntity() && level == null) return null;
        if (anchor.isEntity() && level == null) return anchor.position();
        return anchor.isEntity() ? SpatialAnchorResolver.resolve(anchor, level, partialTick)
                : worldPosition.evaluate(context);
    }
    @Nullable Quaterniond rotation(@Nullable Level level, float partialTick) {
        if (level == null) return anchor.requiresEntity() ? null : new Quaterniond();
        return SpatialAnchorResolver.resolveRotation(anchor, level, partialTick);
    }
}

