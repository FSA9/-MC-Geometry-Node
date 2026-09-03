package com.mine.geometry_node.core.node.nodes.maths;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

public class SnapshotValue extends BaseNode {

    public static final String TYPE_ID = "snapshot_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.snapshot_value"))
                .addRow(new PortRow(null, StandardPorts.GENERIC_VALUE.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.GENERIC_VALUE.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 1. 获取上游数据（不指定具体类，拿到最原始的 Object）
        Object rawInput = getRawInput(context, StandardPorts.GENERIC_VALUE.getId());

        // 2. 如果上游传过来的是包装好的双模数据，强行拆包，只返回死数值
        if (rawInput instanceof DynamicData dyn) {
            return dyn.value();
        }

        // 3. 如果本来就是死数据，原样返回
        return rawInput;
    }
}
