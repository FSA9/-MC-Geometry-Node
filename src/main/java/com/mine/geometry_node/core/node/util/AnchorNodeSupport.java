package com.mine.geometry_node.core.node.util;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionBinding;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchor;
import com.mine.geometry_node.core.engine.spatial.SpatialAnchorResolver;
import com.mine.geometry_node.core.engine.spatial.SpatialTransform;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.engine.graph.value.TypeConverter;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** Shared node presentation and translation-only anchor/offset expression composition. */
public final class AnchorNodeSupport {
    public static final PortDef WORLD_OFFSET = transformPort("world_offset");
    public static final PortDef WORLD_ROTATION = transformPort("world_rotation");
    public static final PortDef LOCAL_OFFSET = transformPort("local_offset");
    public static final PortDef LOCAL_ROTATION = transformPort("local_rotation");
    private static final Pattern IDENTIFIER = Pattern.compile("(?<![A-Za-z0-9_])[A-Za-z_][A-Za-z0-9_]*");

    private AnchorNodeSupport() {}

    public static PortRow row(StandardPorts port) {
        return new PortRow(port.toInput(null), null,
                UIHint.DEFAULT, null, null);
    }

    public static @Nullable SpatialAnchor read(GraphDataContext context, StandardPorts port) {
        var input = context.resolveInput(port.getId());
        return input.connected() ? TypeConverter.convert(input.value(), SpatialAnchor.class, context) : null;
    }

    /** Capture only when creating an object; runtime evaluation must retain this snapshot. */
    public static @Nullable SpatialAnchor capture(GraphDataContext context, StandardPorts port) {
        SpatialAnchor anchor = read(context, port);
        if (anchor == null) return null;
        if (context.getLevel() == null) return anchor.isEntity() ? null : anchor;
        return SpatialAnchorResolver.capture(anchor, context.getLevel());
    }

    public static NodeDef.Builder addTransformInputs(NodeDef.Builder builder) {
        return builder.addPassthroughInput(WORLD_OFFSET, UIHint.VECTOR)
                .addPassthroughInput(WORLD_ROTATION, UIHint.VECTOR)
                .addRow(PortRow.passthrough(LOCAL_OFFSET, UIHint.VECTOR, null, null))
                .addRow(PortRow.passthrough(LOCAL_ROTATION, UIHint.VECTOR, null, null));
    }

    public static SpatialTransform transform(GraphDataContext context) {
        return new SpatialTransform(vector(context, WORLD_OFFSET), vector(context, WORLD_ROTATION),
                vector(context, LOCAL_OFFSET), vector(context, LOCAL_ROTATION));
    }

    public static Vec3 vector(GraphDataContext context, PortDef port) {
        Vec3 value = TypeConverter.convert(context.resolveInput(port.id()).value(), Vec3.class, context);
        return value == null ? Vec3.ZERO : value;
    }

    private static PortDef transformPort(String id) {
        return PortDef.create(id, "geometry_node.port." + id, PortType.XYZ, Vec3.ZERO).liveExpression();
    }

    /** Invalid or unresolved references must never become a world-origin operation. */
    public static boolean isInvalid(GraphDataContext context, StandardPorts port) {
        return read(context, port) == null;
    }

    public static @Nullable ExpressionData readExpression(GraphDataContext context, StandardPorts port) {
        Object value = context.resolveInput(port.getId()).value();
        return value instanceof DynamicData dynamic ? dynamic.expression() : null;
    }

    /** Entity positions are supplied by the runtime; only their local offset enters this value. */
    public static Vec3 position(SpatialAnchor anchor, @Nullable Vec3 offset) {
        Vec3 safeOffset = offset == null ? Vec3.ZERO : offset;
        return anchor.isEntity() ? safeOffset : anchor.position().add(safeOffset);
    }

    /** Preserves both live world coordinates and live offsets without binding-name collisions. */
    public static @Nullable ExpressionData positionExpression(SpatialAnchor anchor,
            @Nullable ExpressionData anchorExpression, @Nullable Vec3 offset,
            @Nullable ExpressionData offsetExpression) {
        ExpressionData base = !anchor.isEntity() && anchorExpression != null && anchorExpression.isVector()
                ? anchorExpression : null;
        ExpressionData delta = offsetExpression != null && offsetExpression.isVector() ? offsetExpression : null;
        if (base == null && delta == null) return null;
        Vec3 baseValue = anchor.isEntity() ? Vec3.ZERO : anchor.position();
        Vec3 deltaValue = offset == null ? Vec3.ZERO : offset;
        Map<String, ExpressionBinding> bindings = new LinkedHashMap<>();
        String[] components = new String[3];
        for (int i = 0; i < 3; i++) {
            String left = component(base, baseValue, i, "anchor_base_", bindings);
            String right = component(delta, deltaValue, i, "anchor_offset_", bindings);
            components[i] = "(" + left + ")+(" + right + ")";
        }
        return ExpressionData.vector(components[0], components[1], components[2], bindings);
    }

    private static String component(@Nullable ExpressionData expression, Vec3 value, int axis,
                                    String prefix, Map<String, ExpressionBinding> bindings) {
        if (expression == null) return Double.toString(switch (axis) {
            case 0 -> value.x;
            case 1 -> value.y;
            default -> value.z;
        });
        expression.bindings().forEach((key, binding) -> bindings.put(prefix + key, binding));
        return IDENTIFIER.matcher(expression.component(axis)).replaceAll(match -> {
            String name = match.group();
            return expression.bindings().containsKey(name) ? prefix + name : name;
        });
    }
}
