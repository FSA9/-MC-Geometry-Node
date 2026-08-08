package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheck;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckContext;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckFactory;
import com.mine.geometry_node.core.engine.blueprint.event.precheck.EventPrecheckRegistry;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.system.quest.QuestEventTypes;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.nodes.events.BaseEventNode;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

import java.util.Map;

public final class OnQuestStatusChanged extends BaseEventNode {
    public static final String TYPE_ID = QuestEventTypes.STATUS_CHANGED;
    public static final String SCOPE_PORT = "quest_scope";
    public static final String SCOPE_GENERAL = "general";
    public static final String SCOPE_SPECIFIC = "specific";

    private static final EventPrecheckFactory SCOPE_PRECHECK = OnQuestStatusChanged::createScopePrecheck;

    public static void registerEventPrecheck() {
        EventPrecheckRegistry.register(TYPE_ID, SCOPE_PRECHECK);
    }

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.QUEST, Component.translatable("geometry_node.node.on_quest_status_changed"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .text("scope_behavior")
                        .input(SCOPE_PORT, "scope")
                        .output(StandardPorts.FLOW_OUT, "flow_out")
                        .output(StandardPorts.ENTITY, "entity")
                        .output(GraphEventFields.TASK_KEY, "task_key")
                        .output(GraphEventFields.INSTANCE_ID, "instance_id")
                        .output(GraphEventFields.OLD_STATUS, "old_status")
                        .output(GraphEventFields.NEW_STATUS, "new_status")
                        .output(GraphEventFields.REASON, "reason")
                        .output(GraphEventFields.REQUEST_SOURCE, "request_source")
                        .build())
                .addRow(new PortRow(null, StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null, StandardPorts.ENTITY.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(
                        PortDef.create(SCOPE_PORT, "geometry_node.port.quest_event_scope",
                                PortType.STRING, SCOPE_GENERAL).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(
                                PortMetaKeys.OPTIONS, new String[]{SCOPE_GENERAL, SCOPE_SPECIFIC},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.quest.event_scope.general",
                                        "geometry_node.quest.event_scope.specific"
                                })
                ))
                .addRow(output(GraphEventFields.TASK_KEY, "geometry_node.port.quest_task_key"))
                .addRow(output(GraphEventFields.INSTANCE_ID, "geometry_node.port.quest_instance_id"))
                .addRow(output(GraphEventFields.OLD_STATUS, "geometry_node.port.quest_old_status"))
                .addRow(output(GraphEventFields.NEW_STATUS, "geometry_node.port.quest_new_status"))
                .addRow(output(GraphEventFields.REASON, "geometry_node.port.quest_reason"))
                .addRow(output(GraphEventFields.REQUEST_SOURCE, "geometry_node.port.quest_request_source"))
                .build();
    }

    private static EventPrecheck createScopePrecheck(EventPrecheckContext context) {
        String scope = context.staticInput(SCOPE_PORT, String.class, SCOPE_GENERAL);
        if (!SCOPE_SPECIFIC.equals(scope)
                || !GraphTypeRegistry.QUEST.id().equals(context.index().getGraphTypeId())) {
            return EventPrecheck.ALWAYS;
        }

        String currentGraphId = context.graphId();
        return (level, target, eventData) -> eventData != null
                && currentGraphId.equals(String.valueOf(
                        eventData.getOrDefault(GraphEventFields.TASK_KEY, "")));
    }

    private static PortRow output(String id, String translationKey) {
        return new PortRow(null, PortDef.create(id, translationKey, PortType.STRING), UIHint.DEFAULT, null, null);
    }
}
