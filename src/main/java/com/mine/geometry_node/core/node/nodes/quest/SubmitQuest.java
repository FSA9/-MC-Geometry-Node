package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.engine.system.quest.model.QuestOperationResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class SubmitQuest extends BaseNode {
    public static final String TYPE_ID = "submit_quest";
    private static final String SUCCESS_PORT = "success";
    private static final String FAILURE_REASON_PORT = "failure_reason";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.submit_quest"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PATH.toInput(""), null, UIHint.PATH, null, null))
                .addRow(new PortRow(null,
                        PortDef.create(SUCCESS_PORT, "geometry_node.port.success", PortType.BOOLEAN),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null,
                        PortDef.create(FAILURE_REASON_PORT, "geometry_node.port.quest_condition_failure_text", PortType.STRING),
                        UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInput(context, StandardPorts.ENTITY.getId(), Entity.class));
        String questPath = getInput(context, StandardPorts.PATH.getId(), String.class);
        QuestOperationResult result = QuestService.INSTANCE.submit(
                owner, questPath, "", QuestService.REQUEST_SOURCE_BLUEPRINT);
        context.setTempData(tempKey(context, SUCCESS_PORT), result.successful());
        context.setTempData(tempKey(context, FAILURE_REASON_PORT), result.message());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (SUCCESS_PORT.equals(portName) || FAILURE_REASON_PORT.equals(portName)) {
            return context.getTempData(tempKey(context, portName));
        }
        return null;
    }

    private static String tempKey(ExecutionContext context, String portName) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + portName;
    }
}
