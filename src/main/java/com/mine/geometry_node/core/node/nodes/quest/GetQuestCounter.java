package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.node.definition.node.NodeComment;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

public final class GetQuestCounter extends BaseNode {
    public static final String TYPE_ID = "get_quest_counter";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.get_quest_counter"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("initializes_missing")
                        .build())
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PATH.toInput(""), null, UIHint.PATH, null, null))
                .addRow(new PortRow(StandardPorts.KEY.toInput(""), StandardPorts.FLOAT_VALUE.toOutput(), UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.FLOAT_VALUE.getId().equals(portName)) return null;
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInput(context, StandardPorts.ENTITY.getId(), Entity.class));
        String questPath = QuestNodeContext.resolveTaskKey(
                context,
                getInput(context, StandardPorts.PATH.getId(), String.class));
        String counterKey = getInput(context, StandardPorts.KEY.getId(), String.class);
        return (float) QuestService.INSTANCE.getOrCreateCounter(owner, questPath, counterKey);
    }
}
