package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.quest.QuestService;
import com.mine.geometry_node.core.engine.quest.model.QuestInstance;
import com.mine.geometry_node.core.engine.quest.status.QuestStatusRegistry;
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

public final class GetQuestStatus extends BaseNode {
    public static final String TYPE_ID = "get_quest_status";
    private static final String STATUS_PORT = "status";
    private static final String EXISTS_PORT = "exists";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.get_quest_status"))
                .addRow(new PortRow(
                        StandardPorts.ENTITY.toInput(),
                        PortDef.create(EXISTS_PORT, "geometry_node.port.exists", PortType.BOOLEAN),
                        UIHint.DEFAULT,
                        null,
                        null
                ))
                .addRow(new PortRow(
                        StandardPorts.PATH.toInput(""),
                        PortDef.create(STATUS_PORT, "geometry_node.port.quest_status", PortType.STRING),
                        UIHint.PATH,
                        null,
                        null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInput(context, StandardPorts.ENTITY.getId(), Entity.class));
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
