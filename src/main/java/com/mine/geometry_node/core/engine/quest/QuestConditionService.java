package com.mine.geometry_node.core.engine.quest;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.GraphProcess;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.quest.model.QuestConditionResult;
import com.mine.geometry_node.core.node.nodes.quest.BaseQuestConditionsNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.List;

/** Evaluates a singleton quest-condition sink without binding or starting the quest graph. */
public final class QuestConditionService {
    public static final QuestConditionService INSTANCE = new QuestConditionService();

    private QuestConditionService() {
    }

    public QuestConditionResult evaluate(Entity owner, String taskKey, QuestConditionKind kind) {
        RuntimeGraphIndex index = GraphEngine.getGraphIndex(taskKey);
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

        GraphProcess process = new GraphProcess(taskKey, index);
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
}
