package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class IF extends BaseNode {

    public static final String TYPE_ID = "if_branch";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                根据布尔输入选择执行分支。
                bool 为 true 时执行 true 分支。
                bool 为 false 或空值时执行 false 分支。""";

        return NodeDef.builder(TYPE_ID, NodeType.FLOW_CONTROL, Component.translatable("geometry_node.node.if_branch"))
                .comment(comment)
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        StandardPorts.FLOW_TRUE.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        StandardPorts.BOOL.toInput(),
                        StandardPorts.FLOW_FALSE.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Boolean isTrue = getInput(context, StandardPorts.BOOL.getId(), Boolean.class);

        if (Boolean.TRUE.equals(isTrue)) {
            return next(StandardPorts.FLOW_TRUE.getId());
        } else {
            return next(StandardPorts.FLOW_FALSE.getId());
        }
    }
}
