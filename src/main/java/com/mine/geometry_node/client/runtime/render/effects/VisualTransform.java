package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import com.mine.geometry_node.core.node.util.AnchorNodeSupport;
import com.mine.geometry_node.core.node.util.VisualTransformInputs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;

/** One live transform chain shared by all single-anchor temporary visuals. */
record VisualTransform(VisualAnchor anchor, LiveValue.State<Vec3> worldOffset,
                       LiveValue.State<Vec3> worldRotation, LiveValue.State<Vec3> localOffset,
                       LiveValue.State<Vec3> localRotation) {
    static VisualTransform capture(AbstractVisualEffect effect, CompoundTag data) {
        return new VisualTransform(effect.captureAnchor(data, "anchor"),
                effect.captureXyz(AnchorNodeSupport.WORLD_OFFSET, "worldOffset", VisualTransformInputs.read(data, "worldOffset")),
                effect.captureXyz(AnchorNodeSupport.WORLD_ROTATION, "worldRotation", VisualTransformInputs.read(data, "worldRotation")),
                effect.captureXyz(AnchorNodeSupport.LOCAL_OFFSET, "localOffset", VisualTransformInputs.read(data, "localOffset")),
                effect.captureXyz(AnchorNodeSupport.LOCAL_ROTATION, "localRotation", VisualTransformInputs.read(data, "localRotation")));
    }

    @Nullable Frame resolve(@Nullable Level level, float partialTick, ExpressionEvaluationContext context) {
        Vec3 base = anchor.resolve(level, partialTick, context);
        Quaterniond rotation = anchor.rotation(level, partialTick);
        if (base == null || rotation == null) return null;
        SpatialTransform transform = new SpatialTransform(worldOffset.evaluate(context), worldRotation.evaluate(context),
                localOffset.evaluate(context), localRotation.evaluate(context));
        return new Frame(transform.position(base, rotation), transform.orientation(rotation));
    }

    record Frame(Vec3 position, Quaterniond orientation) { }
}
