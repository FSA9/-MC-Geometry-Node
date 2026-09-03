package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
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

import java.util.Map;

public final class SetQuestStatus extends BaseNode {
    public static final String TYPE_ID = "set_quest_status";
    private static final String STATUS_PORT = "status";
    private static final String REASON_PORT = "reason";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.set_quest_status"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH)
                .addPassthroughInput(PortDef.create(STATUS_PORT, "geometry_node.port.quest_status", PortType.STRING,
                                QuestStatusRegistry.IN_PROGRESS.id()).hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.DYNAMIC_REGISTRY_ID,
                                QuestStatusRegistry.ASSIGNABLE_DYNAMIC_REGISTRY_ID))
                .addPassthroughInput(PortDef.create(REASON_PORT, "geometry_node.port.quest_reason", PortType.STRING, ""), UIHint.INPUT)
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
