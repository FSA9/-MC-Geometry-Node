package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class ReflectVector extends BaseNode {

    public static final String TYPE_ID = "reflect_vector";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.reflect_vector"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.VECTOR, "output_vector")
                        .input(StandardPorts.VECTOR, "input_vector")
                        .input(StandardPorts.NORMAL, "normal")
                        .build())
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.VECTOR, null, null))
                .addPassthroughInput(StandardPorts.NORMAL.toInput(), UIHint.VECTOR, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.VECTOR.getId().equals(portName)) {
            return null;
        }

        Vec3 vector = getInput(context, StandardPorts.VECTOR.getId(), Vec3.class);
        Vec3 normal = getInput(context, StandardPorts.NORMAL.getId(), Vec3.class);
        if (vector == null) {
            vector = Vec3.ZERO;
        }
        if (normal == null || normal.lengthSqr() < 1.0E-7D) {
            return vector;
        }

        Vec3 unitNormal = normal.normalize();
        return vector.subtract(unitNormal.scale(2.0D * vector.dot(unitNormal)));
    }
}
