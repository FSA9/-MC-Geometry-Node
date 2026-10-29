package com.mine.geometry_node.core.node.nodes.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.system.quest.QuestService;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestOperationResult;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortDef;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Map;

public final class SetQuestCondition extends BaseNode {
    public static final String TYPE_ID = "set_quest_condition";
    public static final String CONDITION_KIND_PORT = "condition_kind";
    public static final String STATE_PORT = "state";
    public static final String SUCCESS_PORT = "success";

    @Override
    public NodeDef getDefaultDefinition() {
        return definition(QuestConditionKind.VISIBILITY);
    }

    @Override
    public NodeDef getDefinition(NodeData data) {
        return definition(parseKind(data == null || data.inputs == null
                ? null : data.inputs.get(CONDITION_KIND_PORT)));
    }

    private static NodeDef definition(QuestConditionKind kind) {
        String[] stateLabels = switch (kind) {
            case VISIBILITY -> new String[]{"visible", "hidden"};
            case ACCEPTANCE -> new String[]{"acceptable", "unacceptable"};
            case COMPLETION -> new String[]{"completable", "uncompletable"};
        };
        return NodeDef.builder(TYPE_ID, NodeType.QUEST,
                        Component.translatable("geometry_node.node.set_quest_condition"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(),
                        UIHint.DEFAULT, null, null))
                .addRow(new PortRow(null,
                        PortDef.create(SUCCESS_PORT, "geometry_node.port.success", PortType.BOOLEAN),
                        UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ENTITY.toInput(), UIHint.DEFAULT)
                .addPassthroughInput(StandardPorts.PATH.toInput(""), UIHint.PATH)
                .addStaticInput(PortDef.create(CONDITION_KIND_PORT, "geometry_node.port.quest_condition_kind",
                                PortType.STRING, "visibility").hiddenPin(), UIHint.SELECT, null,
                        Map.of(PortMetaKeys.OPTIONS, new String[]{"visibility", "acceptance", "completion"},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.quest.condition_kind.visibility",
                                        "geometry_node.quest.condition_kind.acceptance",
                                        "geometry_node.quest.condition_kind.completion"}))
                .addPassthroughInput(PortDef.create(STATE_PORT, "geometry_node.port.quest_condition_state",
                                PortType.BOOLEAN, true), UIHint.SELECT, null,
                        Map.of(PortMetaKeys.CONNECTABLE_SELECT, true,
                                PortMetaKeys.OPTIONS, new String[]{"true", "false"},
                                PortMetaKeys.OPTION_LABELS, new String[]{
                                        "geometry_node.quest.condition_state." + stateLabels[0],
                                        "geometry_node.quest.condition_state." + stateLabels[1]}))
                .build();
    }

    static QuestConditionKind parseKind(Object value) {
        if (value == null) return QuestConditionKind.VISIBILITY;
        return switch (value.toString()) {
            case "visibility" -> QuestConditionKind.VISIBILITY;
            case "acceptance" -> QuestConditionKind.ACCEPTANCE;
            case "completion" -> QuestConditionKind.COMPLETION;
            default -> throw new IllegalArgumentException("Unsupported quest condition kind: " + value);
        };
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        Entity owner = QuestNodeContext.resolveOwner(context,
                getInputFromList(context, StandardPorts.ENTITY.getId(), 0, Entity.class));
        String questPath = getInput(context, StandardPorts.PATH.getId(), String.class);
        QuestConditionKind kind = parseKind(context.getStaticInput(CONDITION_KIND_PORT));
        Boolean state = getInput(context, STATE_PORT, Boolean.class);
        QuestOperationResult result = QuestService.INSTANCE.setCondition(
                owner, questPath, kind, Boolean.TRUE.equals(state));
        context.setNodeResult(SUCCESS_PORT, result.successful());
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        return SUCCESS_PORT.equals(portName) ? context.getNodeResult(SUCCESS_PORT) : null;
    }
}
