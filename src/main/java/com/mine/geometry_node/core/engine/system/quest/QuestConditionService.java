package com.mine.geometry_node.core.engine.system.quest;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.runtime.BlueprintProcess;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.compile.artifact.CompiledDataIndex;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionCheck;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionResult;
import com.mine.geometry_node.core.node.meta.StaticKeys;
import com.mine.geometry_node.core.node.nodes.quest.BaseQuestConditionsNode;
import com.mine.geometry_node.core.node.value.QuestConditionValue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.List;

/** Evaluates a singleton quest-condition sink without binding or starting the quest graph. */
public final class QuestConditionService {
    public static final QuestConditionService INSTANCE = new QuestConditionService();

    private QuestConditionService() {
    }

    public QuestConditionResult evaluate(Entity owner, String taskKey, QuestConditionKind kind) {
        BlueprintPlan index = BlueprintRuntime.INSTANCE.getGraphIndex(taskKey);
        if (index == null || owner == null || kind == null
                || !(owner.level() instanceof ServerLevel level)) {
            return QuestConditionResult.evaluationFailed();
        }

        List<Integer> conditionNodes = index.findNodesByType(kind.nodeTypeId());
        if (conditionNodes.isEmpty()) {
            return QuestConditionResult.passed();
        }
        if (conditionNodes.size() != 1) {
            GeometryNode.LOGGER.error(
                    "Quest graph must contain at most one condition node of each kind: taskKey={}, kind={}, count={}",
                    taskKey,
                    kind,
                    conditionNodes.size());
            return QuestConditionResult.evaluationFailed();
        }

        BlueprintProcess process = new BlueprintProcess(taskKey, index);
        process.setGraphOwner(owner);
        process.setEnvironment(level, owner);
        try {
            Object result = process.evaluateDataOutput(
                    conditionNodes.getFirst(), BaseQuestConditionsNode.RESULT_PORT);
            if (result instanceof QuestConditionResult conditionResult) {
                return conditionResult;
            }
            GeometryNode.LOGGER.error(
                    "Quest condition node returned an invalid result: taskKey={}, kind={}, resultType={}",
                    taskKey,
                    kind,
                    result == null ? "null" : result.getClass().getName());
            return QuestConditionResult.evaluationFailed();
        } finally {
            process.shutdown("quest_condition_check_finished");
        }
    }

    /** Evaluates every authored condition for a read-only quest-screen snapshot. */
    public List<QuestConditionCheck> evaluateChecks(Entity owner, String taskKey, QuestConditionKind kind) {
        BlueprintPlan index = BlueprintRuntime.INSTANCE.getGraphIndex(taskKey);
        if (index == null || owner == null || kind == null
                || !(owner.level() instanceof ServerLevel level)) {
            return List.of();
        }

        List<Integer> conditionNodes = index.findNodesByType(kind.nodeTypeId());
        if (conditionNodes.isEmpty()) return List.of();
        if (conditionNodes.size() != 1) {
            GeometryNode.LOGGER.error(
                    "Quest graph must contain at most one condition node of each kind: taskKey={}, kind={}, count={}",
                    taskKey,
                    kind,
                    conditionNodes.size());
            return List.of();
        }

        int conditionNodeId = conditionNodes.getFirst();
        int conditionCount = BaseQuestConditionsNode.resolveConditionCount(
                index.getNodeStaticInput(
                        conditionNodeId,
                        StaticKeys.DYNAMIC_BRANCH_INPUT_COUNT.id()));
        List<QuestConditionCheck> checks = new ArrayList<>(conditionCount);
        BlueprintProcess process = new BlueprintProcess(taskKey, index);
        process.setGraphOwner(owner);
        process.setEnvironment(level, owner);
        try {
            for (int i = 1; i <= conditionCount; i++) {
                CompiledDataIndex.DataConnectionSource source = index.findInputSource(
                        conditionNodeId,
                        BaseQuestConditionsNode.conditionPort(i));
                if (source == null) continue;

                Object raw = process.evaluateDataOutput(source.sourceNodeId(), source.sourcePortName());
                if (raw instanceof QuestConditionValue condition && !condition.displayText().isBlank()) {
                    checks.add(new QuestConditionCheck(
                            condition.displayText(),
                            true,
                            condition.allowed()));
                }
            }
            return List.copyOf(checks);
        } finally {
            process.shutdown("quest_condition_screen_check_finished");
        }
    }
}
