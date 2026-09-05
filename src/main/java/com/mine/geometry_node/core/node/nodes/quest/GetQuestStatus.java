package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.engine.system.quest.model.QuestInstance;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
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

public final class GetQuestStatus extends BaseNode {
    public static final String TYPE_ID = "get_quest_status";
    private static final String STATUS_PORT = "status";
    private static final String EXISTS_PORT = "exists";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.get_quest_status"))
                .addRow(new PortRow(null,
                        PortDef.create(EXISTS_PORT, "geometry_node.port.exists", PortType.BOOLEAN),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null,
                        PortDef.create(STATUS_PORT, "geometry_node.port.quest_status", PortType.STRING),
                        UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class));
        String questPath = getInput(context, StandardPorts.PATH.getId(), String.class);
        if (owner == null || questPath == null || questPath.isBlank()) {
            if (EXISTS_PORT.equals(portName)) return false;
            if (STATUS_PORT.equals(portName)) return "";
            return null;
        }

        QuestInstance instance = QuestService.INSTANCE.findCurrent(owner, questPath);
        if (instance != null) {
            if (EXISTS_PORT.equals(portName)) return true;
            if (STATUS_PORT.equals(portName)) return instance.statusId();
            return null;
        }

        boolean listed = QuestService.INSTANCE.findListEntry(owner, questPath) != null;
        if (EXISTS_PORT.equals(portName)) return listed;
        if (STATUS_PORT.equals(portName)) {
            return listed ? QuestStatusRegistry.UNACCEPTED.id() : "";
        }
        return null;
    }
}
