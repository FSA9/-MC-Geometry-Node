package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.*;
import net.minecraft.network.chat.Component;

public class TriggerBlueprint extends BaseNode {

    public static final String TYPE_ID = "trigger_blueprint";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.trigger_blueprint"))
                .addRow(new PortRow(
                        StandardPorts.FLOW_IN.toExec(),
                        StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(
                        PortDef.create("frequency", "geometry_node.port.frequency", PortType.STRING, ""),
                        null, UIHint.INPUT, null, null
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        // 获取发送的频率名称 (支持连线动态传入，也支持输入框静态读取)
        String frequency = getInput(context, "frequency", String.class);

        if (frequency != null && !frequency.trim().isEmpty()) {
            com.mine.geometry_node.core.execution.GraphEngine.dispatchCustomEvent(
                    context.getLevel(),
                    context.getEntity(),
                    frequency,
                    null // 暂不需要传参，传null即可
            );
        }

        // 无论是否广播成功，当前图的执行流继续往后走
        return next(StandardPorts.FLOW_OUT.getId());
    }
}