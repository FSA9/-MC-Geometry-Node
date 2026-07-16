package com.mine.geometry_node.core.node.nodes.maths.vector;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class ReflectVector extends BaseNode {

    public static final String TYPE_ID = "reflect_vector";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                按法线反射输入向量。
                常用于 projectile 命中区域后的反弹速度计算。
                normal 会自动归一化；normal 为空或零向量时原样输出 vector。""";

        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.reflect_vector"))
                .comment(comment)
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInput(), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(StandardPorts.NORMAL.toInput(), null, UIHint.VECTOR, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
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
