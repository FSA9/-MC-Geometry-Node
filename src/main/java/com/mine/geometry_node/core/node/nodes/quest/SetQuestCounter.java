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

public final class SetQuestCounter extends BaseNode {
    public static final String TYPE_ID = "set_quest_counter";
    private static final String SUCCESS_PORT = "success";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.set_quest_counter"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PATH.toInput(""), null, UIHint.PATH, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.FLOAT_VALUE.toInput(0.0f), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(null, PortDef.create(SUCCESS_PORT, "geometry_node.port.success", PortType.BOOLEAN), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInput(context, StandardPorts.ENTITY.getId(), Entity.class));
        String questPath = QuestNodeContext.resolveTaskKey(
                context,
                getInput(context, StandardPorts.PATH.getId(), String.class));
        String counterKey = getInput(context, StandardPorts.KEY.getId(), String.class);
        Float value = getInput(context, StandardPorts.FLOAT_VALUE.getId(), Float.class);
        QuestOperationResult result = QuestService.INSTANCE.setCounter(
                owner, questPath, counterKey, value != null ? value.doubleValue() : 0.0);
        context.setTempData(tempKey(context), result.successful());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        return SUCCESS_PORT.equals(portName) ? context.getTempData(tempKey(context)) : null;
    }

    private static String tempKey(ExecutionContext context) {
        return TYPE_ID + ":" + context.getCurrentNodeId() + ":" + SUCCESS_PORT;
    }
}
