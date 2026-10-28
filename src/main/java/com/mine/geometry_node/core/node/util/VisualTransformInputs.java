package com.mine.geometry_node.core.node.util;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

/** Shared live transform payload for temporary visual effects. */
public final class VisualTransformInputs {
    private VisualTransformInputs() { }

    public static SpatialTransform write(GraphDataContext context, CompoundTag data,
                                         Map<String, ExpressionData> expressions) {
        Vec3 worldOffset = write(context, data, expressions, AnchorNodeSupport.WORLD_OFFSET, "worldOffset");
        Vec3 worldRotation = write(context, data, expressions, AnchorNodeSupport.WORLD_ROTATION, "worldRotation");
        Vec3 localOffset = write(context, data, expressions, AnchorNodeSupport.LOCAL_OFFSET, "localOffset");
        Vec3 localRotation = write(context, data, expressions, AnchorNodeSupport.LOCAL_ROTATION, "localRotation");
        return new SpatialTransform(worldOffset, worldRotation, localOffset, localRotation);
    }

    public static Vec3 write(GraphDataContext context, CompoundTag data, Map<String, ExpressionData> expressions,
                              PortDef port, String key) {
        Object snapshot = context.resolveInput(port.id()).value();
        if (snapshot instanceof DynamicData dynamic) {
            snapshot = dynamic.value();
            if (dynamic.expression() != null) expressions.put(key, dynamic.expression());
        }
        Vec3 value = TypeConverter.convert(snapshot, Vec3.class, context);
        if (value == null) value = Vec3.ZERO;
        data.putDouble(key + "X", value.x);
        data.putDouble(key + "Y", value.y);
        data.putDouble(key + "Z", value.z);
        return value;
    }

    public static Vec3 read(CompoundTag data, String key) {
        return new Vec3(data.getDoubleOr(key + "X", 0), data.getDoubleOr(key + "Y", 0), data.getDoubleOr(key + "Z", 0));
    }
}
