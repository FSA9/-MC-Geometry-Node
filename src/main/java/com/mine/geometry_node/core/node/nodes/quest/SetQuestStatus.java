package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.quest.QuestService;
import com.mine.geometry_node.core.engine.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
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

import java.util.Map;

public final class SetQuestStatus extends BaseNode {
    public static final String TYPE_ID = "set_quest_status";
    private static final String STATUS_PORT = "status";
    private static final String REASON_PORT = "reason";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_quest_status"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PATH.toInput(""), null, UIHint.PATH, null, null))
                .addRow(new PortRow(
                        PortDef.create(STATUS_PORT, "geometry_node.port.quest_status", PortType.STRING,
                                QuestStatusRegistry.IN_PROGRESS.id()).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID,
                                QuestStatusRegistry.ASSIGNABLE_DYNAMIC_REGISTRY_ID)
                ))
                .addRow(new PortRow(
                        PortDef.create(REASON_PORT, "geometry_node.port.quest_reason", PortType.STRING, ""),
                        null,
                        UIHint.INPUT,
                        null,
                        null
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity owner = QuestNodeContext.resolveOwner(
                context,
                getInput(context, StandardPorts.ENTITY.getId(), Entity.class));
        String questPath = getInput(context, StandardPorts.PATH.getId(), String.class);
        String statusId = getInput(context, STATUS_PORT, String.class);
        String reason = getInput(context, REASON_PORT, String.class);
        QuestService.INSTANCE.forceSetStatus(
                owner, questPath, statusId, reason, QuestService.REQUEST_SOURCE_BLUEPRINT);
        return next(StandardPorts.FLOW_OUT.getId());
    }
}
