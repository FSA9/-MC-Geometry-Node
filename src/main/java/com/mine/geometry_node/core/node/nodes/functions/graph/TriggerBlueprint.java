package com.mine.geometry_node.core.node.nodes.functions.graph;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.definition.port.*;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class TriggerBlueprint extends BaseNode {

    public static final String TYPE_ID = "trigger_blueprint";

    @Override
    public NodeDef getDefaultDefinition() {
        // 作为 Action 节点，保留输入端口是非常正确的设计，允许蓝图动态构建事件名
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
        String frequency = getInput(context, "frequency", String.class);

        if (frequency != null && !frequency.trim().isEmpty()) {
            BlueprintRuntime.INSTANCE.dispatchCustomEvent(
                    context.getLevel(),
                    frequency,
                    Map.of(StandardPorts.TRIGGER_ENTITY.getId(), context.getEntity())
            );
        }

        // 无论是否广播成功，当前图的执行流继续往后走
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
