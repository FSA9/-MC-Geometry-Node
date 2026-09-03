package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatus;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public final class GetRegisteredQuestStatus extends BaseNode {
    public static final String TYPE_ID = "get_registered_quest_status";
    private static final String SELECTED_STATUS_PORT = "selected_status";
    private static final String STATUS_PORT = "status";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST,
                        Component.translatable("geometry_node.node.get_registered_quest_status"))
                .addRow(new PortRow(null,
                        PortDef.create(STATUS_PORT, "geometry_node.port.quest_status", PortType.STRING),
                        UIHint.DEFAULT, null, null))
                .addPassthroughInput(
                        PortDef.create(SELECTED_STATUS_PORT, "geometry_node.port.quest_status",
                                PortType.STRING, QuestStatusRegistry.IN_PROGRESS.id()).hiddenPin(),
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID,
                                QuestStatusRegistry.DYNAMIC_REGISTRY_ID))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!STATUS_PORT.equals(portName)) return null;
        String selected = getInput(context, SELECTED_STATUS_PORT, String.class);
        QuestStatus status = QuestStatusRegistry.INSTANCE.get(selected);
        return status != null ? status.id() : QuestStatusRegistry.IN_PROGRESS.id();
    }
}
