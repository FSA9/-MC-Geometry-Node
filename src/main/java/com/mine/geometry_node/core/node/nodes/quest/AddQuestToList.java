package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.engine.system.quest.model.QuestOperationResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class AddQuestToList extends BaseNode {
    public static final String TYPE_ID = "add_quest_to_list";
    private static final String SUCCESS_PORT = "success";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.add_quest_to_list"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, PortDef.create(SUCCESS_PORT, "geometry_node.port.success", PortType.BOOLEAN), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class));
        String questPath = getInput(context, StandardPorts.PATH.getId(), String.class);
        QuestOperationResult result = QuestService.INSTANCE.addToList(owner, questPath);
        context.setNodeResult(SUCCESS_PORT, result.successful());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return SUCCESS_PORT.equals(portName) ? context.getNodeResult(SUCCESS_PORT) : null;
    }
}
