package com.mine.geometry_node.core.node.nodes.functions.vector;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

public class VectorAdd extends BaseNode {

    public static final String TYPE_ID = "vector_add";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.vector_add"))
                .addRow(new PortRow(null, StandardPorts.VECTOR.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInputWithIndex(1), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.VECTOR.toInputWithIndex(2), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        Vec3 a = getInput(context, StandardPorts.VECTOR.getIdWithIndex(1), Vec3.class);
        Vec3 b = getInput(context, StandardPorts.VECTOR.getIdWithIndex(2), Vec3.class);

        if (a == null) a = Vec3.ZERO;
        if (b == null) b = Vec3.ZERO;

        return a.add(b);
    }
}