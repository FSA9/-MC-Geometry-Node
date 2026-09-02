package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

/** General-purpose vector arithmetic node. */
public class VectorOperation extends BaseNode {
    public static final String TYPE_ID = "vector_operation";
    private static final String[] OPERATORS = {
            "add", "subtract", "multiply", "divide",
            "add_scalar", "subtract_scalar", "multiply_scalar", "divide_scalar",
            "dot", "cross", "length", "normalize", "distance", "lerp", "negate", "reflect",
            "vector_to_direction", "direction_to_vector"
    };
    private static final String[] OPERATOR_LABEL_KEYS = {
            "geometry_node.vector.operation.add", "geometry_node.vector.operation.subtract",
            "geometry_node.vector.operation.multiply", "geometry_node.vector.operation.divide",
            "geometry_node.vector.operation.add_scalar", "geometry_node.vector.operation.subtract_scalar",
            "geometry_node.vector.operation.multiply_scalar", "geometry_node.vector.operation.divide_scalar",
            "geometry_node.vector.operation.dot", "geometry_node.vector.operation.cross",
            "geometry_node.vector.operation.length", "geometry_node.vector.operation.normalize",
            "geometry_node.vector.operation.distance", "geometry_node.vector.operation.lerp",
            "geometry_node.vector.operation.negate", "geometry_node.vector.operation.reflect",
            "geometry_node.vector.operation.vector_to_direction",
            "geometry_node.vector.operation.direction_to_vector"
    };

    @Override public NodeDef getDefaultDefinition() { return buildDef("add"); }

    @Override public NodeDef getDefinition(NodeData data) {
        String op = data != null && data.inputs.get(StandardPorts.STRING.getId()) instanceof String s ? s : "add";
        return buildDef(op);
    }

    private NodeDef buildDef(String op) {
        boolean vectorToDirection = op.equals("vector_to_direction");
        boolean directionToVector = op.equals("direction_to_vector");
        boolean scalar = switch (op) { case "dot", "length", "distance" -> true; default -> false; };
        NodeDef.Builder b = NodeDef.builder(TYPE_ID, NodeType.MATH,
                Component.translatable("geometry_node.node.vector_operation"));
        if (vectorToDirection) {
            b.addRow(new PortRow(null, StandardPorts.PITCH.toOutput(), UIHint.DEFAULT, null, null));
            b.addRow(new PortRow(null, StandardPorts.YAW.toOutput(), UIHint.DEFAULT, null, null));
            b.addRow(new PortRow(null, StandardPorts.LENGTH.toOutput(), UIHint.DEFAULT, null, null));
        } else {
            b.addRow(new PortRow(null, (scalar ? StandardPorts.FLOAT_VALUE : StandardPorts.VECTOR).toOutput(), UIHint.DEFAULT, null, null));
        }
        b.addRow(new PortRow(StandardPorts.STRING.toInput("add").hiddenPin(), null, UIHint.SELECT, null,
                Map.of(PortMetaKeys.OPTIONS, OPERATORS, PortMetaKeys.OPTION_LABELS, OPERATOR_LABEL_KEYS)));
        if (directionToVector) {
            b.addRow(new PortRow(StandardPorts.PITCH.toInput(0.0f), null, UIHint.INPUT, null, null));
            b.addRow(new PortRow(StandardPorts.YAW.toInput(0.0f), null, UIHint.INPUT, null, null));
            b.addRow(new PortRow(StandardPorts.LENGTH.toInput(1.0f), null, UIHint.INPUT, null, null));
            return b.build();
        }
        b.addRow(new PortRow(StandardPorts.VECTOR.toInputWithIndex(1), null, UIHint.VECTOR, null, null));
        if (op.equals("add_scalar") || op.equals("subtract_scalar")
                || op.equals("multiply_scalar") || op.equals("divide_scalar") || op.equals("lerp"))
            b.addRow(new PortRow(StandardPorts.FLOAT_VALUE.toInputWithIndex(2), null, UIHint.INPUT, null, null));
        else if (!op.equals("length") && !op.equals("normalize") && !op.equals("negate"))
            b.addRow(new PortRow(StandardPorts.VECTOR.toInputWithIndex(2), null, UIHint.VECTOR, null, null));
        return b.build();
    }

    @Override public Object compute(ExecutionContext c, String port) {
        String op = getInput(c, StandardPorts.STRING.getId(), String.class);
        if (op == null) op = "add";
        if (op.equals("direction_to_vector")) {
            Float pitch = getInput(c, StandardPorts.PITCH.getId(), Float.class);
            Float yaw = getInput(c, StandardPorts.YAW.getId(), Float.class);
            Float length = getInput(c, StandardPorts.LENGTH.getId(), Float.class);
            return Vec3.directionFromRotation(pitch == null ? 0.0f : pitch, yaw == null ? 0.0f : yaw)
                    .scale(length == null ? 1.0 : length);
        }
        Vec3 a = getInput(c, StandardPorts.VECTOR.getIdWithIndex(1), Vec3.class);
        if (a == null) a = Vec3.ZERO;
        if (op.equals("vector_to_direction")) {
            double length = a.length();
            if (StandardPorts.LENGTH.getId().equals(port)) return (float) length;
            if (length < 1.0E-12) return 0.0f;
            double horizontal = Math.sqrt(a.x * a.x + a.z * a.z);
            if (StandardPorts.PITCH.getId().equals(port)) return (float) Math.toDegrees(Math.atan2(-a.y, horizontal));
            if (StandardPorts.YAW.getId().equals(port)) return (float) Math.toDegrees(Math.atan2(-a.x, a.z));
            return null;
        }
        Vec3 b = getInput(c, StandardPorts.VECTOR.getIdWithIndex(2), Vec3.class);
        if (b == null) b = Vec3.ZERO;
        Float s = getInput(c, StandardPorts.FLOAT_VALUE.getIdWithIndex(2), Float.class);
        double scalar = s == null ? 0.0 : s;
        return switch (op) {
            case "add" -> a.add(b);
            case "subtract" -> a.subtract(b);
            case "multiply" -> new Vec3(a.x * b.x, a.y * b.y, a.z * b.z);
            case "divide" -> new Vec3(b.x == 0 ? 0 : a.x / b.x, b.y == 0 ? 0 : a.y / b.y, b.z == 0 ? 0 : a.z / b.z);
            case "multiply_scalar" -> a.scale(scalar);
            case "add_scalar" -> new Vec3(a.x + scalar, a.y + scalar, a.z + scalar);
            case "subtract_scalar" -> new Vec3(a.x - scalar, a.y - scalar, a.z - scalar);
            case "divide_scalar" -> scalar == 0 ? Vec3.ZERO : a.scale(1.0 / scalar);
            case "dot" -> (float) a.dot(b);
            case "cross" -> a.cross(b);
            case "length" -> (float) a.length();
            case "normalize" -> a.lengthSqr() < 1.0E-12 ? Vec3.ZERO : a.normalize();
            case "distance" -> (float) a.distanceTo(b);
            case "lerp" -> a.lerp(b, scalar);
            case "negate" -> a.scale(-1);
            case "reflect" -> { Vec3 n = b.lengthSqr() < 1.0E-12 ? Vec3.ZERO : b.normalize(); yield a.subtract(n.scale(2 * a.dot(n))); }
            default -> a;
        };
    }
}
