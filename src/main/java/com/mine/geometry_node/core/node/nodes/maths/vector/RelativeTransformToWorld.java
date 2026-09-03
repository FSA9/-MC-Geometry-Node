package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** Converts a position and rotation expressed in a local frame into world space. */
public final class RelativeTransformToWorld extends BaseNode {
    public static final String TYPE_ID = "relative_transform_to_world";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH,
                        Component.translatable("geometry_node.node.relative_transform_to_world"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.WORLD_POSITION, "world_position")
                        .output(StandardPorts.WORLD_ROTATION, "world_rotation")
                        .input(StandardPorts.BASE_POSITION, "base_position")
                        .input(StandardPorts.BASE_ROTATION, "base_rotation")
                        .input(StandardPorts.POSITION_OFFSET, "position_offset")
                        .input(StandardPorts.ROTATION_OFFSET, "rotation_offset")
                        .build())
                .addRow(new PortRow(null, StandardPorts.WORLD_POSITION.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.WORLD_ROTATION.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BASE_POSITION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.BASE_ROTATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.POSITION_OFFSET.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.ROTATION_OFFSET.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        Vec3 basePosition = inputOrZero(context, StandardPorts.BASE_POSITION);
        Vec3 baseRotation = inputOrZero(context, StandardPorts.BASE_ROTATION);
        Vec3 positionOffset = inputOrZero(context, StandardPorts.POSITION_OFFSET);
        Vec3 rotationOffset = inputOrZero(context, StandardPorts.ROTATION_OFFSET);

        Quaternionf baseQuaternion = quaternion(baseRotation);
        if (StandardPorts.WORLD_POSITION.getId().equals(portName)) {
            Vector3f transformed = baseQuaternion.transform(
                    (float) positionOffset.x, (float) positionOffset.y, (float) positionOffset.z,
                    new Vector3f());
            return basePosition.add(transformed.x, transformed.y, transformed.z);
        }
        if (StandardPorts.WORLD_ROTATION.getId().equals(portName)) {
            Quaternionf worldQuaternion = baseQuaternion.mul(quaternion(rotationOffset), new Quaternionf());
            Vector3f euler = worldQuaternion.getEulerAnglesYXZ(new Vector3f());
            return new Vec3(
                    Math.toDegrees(euler.x),
                    -Math.toDegrees(euler.y),
                    Math.toDegrees(euler.z));
        }
        return null;
    }

    private Vec3 inputOrZero(ExecutionContext context, StandardPorts port) {
        Vec3 value = getInput(context, port.getId(), Vec3.class);
        return value != null ? value : Vec3.ZERO;
    }

    private static Quaternionf quaternion(Vec3 rotation) {
        return new Quaternionf().rotationYXZ(
                (float) Math.toRadians(-rotation.y),
                (float) Math.toRadians(rotation.x),
                (float) Math.toRadians(rotation.z));
    }
}
