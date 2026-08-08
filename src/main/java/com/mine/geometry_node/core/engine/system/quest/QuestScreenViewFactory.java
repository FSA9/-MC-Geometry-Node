package com.mine.geometry_node.core.engine.system.quest;

import com.mine.geometry_node.core.engine.system.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionOverview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionCheck;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestRewardDefinition;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatus;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.function.Function;

/** Maps quest domain models to immutable screen views for both live sessions and previews. */
public final class QuestScreenViewFactory {
    private QuestScreenViewFactory() {
    }

    public static List<PacketQuestScreenSnapshot.StatusView> statuses() {
        List<PacketQuestScreenSnapshot.StatusView> result = new ArrayList<>();
        for (QuestStatus status : QuestStatusRegistry.INSTANCE.all()) {
            result.add(new PacketQuestScreenSnapshot.StatusView(
                    status.id(), status.translationKey(), status.color(), status.graphActive()));
        }
        return List.copyOf(result);
    }

    public static PacketQuestScreenSnapshot.QuestView quest(String taskKey,
                                                            String instanceId,
                                                            String statusId,
                                                            boolean acceptEnabled,
                                                            long updatedAt,
                                                            QuestDefinition definition,
                                                            QuestConditionOverview conditionOverview,
                                                            @Nullable Function<QuestConditionKind, List<QuestConditionCheck>> conditionResolver,
                                                            @Nullable ToDoubleFunction<String> counterResolver) {
        QuestDefinition safeDefinition = definition == null ? QuestDefinition.EMPTY : definition;
        QuestConditionOverview safeConditions = conditionOverview != null
                ? conditionOverview
                : QuestConditionOverview.EMPTY;
        String safeTaskKey = taskKey == null ? "" : taskKey;
        DialogueText title = safeDefinition.title().plain().isBlank()
                ? DialogueText.plain(safeTaskKey)
                : DialogueText.rich(safeDefinition.title());
        List<PacketQuestScreenSnapshot.ObjectiveView> objectives = new ArrayList<>();
        for (QuestObjectiveDefinition objective : safeDefinition.objectives()) {
            double currentValue = objective.counterEnabled() && counterResolver != null
                    ? counterResolver.applyAsDouble(objective.counterKey())
                    : 0.0;
            objectives.add(new PacketQuestScreenSnapshot.ObjectiveView(
                    objective.entryId(),
                    DialogueText.rich(objective.content()),
                    objective.quantityEnabled(),
                    objective.counterEnabled(),
                    objective.counterKey(),
                    currentValue,
                    objective.targetValue(),
                    objective.hintType().id(),
                    objective.hintValue()
            ));
        }
        List<PacketQuestScreenSnapshot.RewardView> rewards = new ArrayList<>();
        for (QuestRewardDefinition reward : safeDefinition.rewards()) {
            double amount = reward.counterEnabled()
                    ? counterResolver == null ? 0.0 : counterResolver.applyAsDouble(reward.counterKey())
                    : reward.amount();
            rewards.add(new PacketQuestScreenSnapshot.RewardView(
                    reward.entryId(),
                    DialogueText.rich(reward.content()),
                    amount,
                    reward.hintType().id(),
                    reward.hintValue()
            ));
        }
        return new PacketQuestScreenSnapshot.QuestView(
                safeTaskKey,
                instanceId,
                statusId,
                title,
                DialogueText.rich(safeDefinition.description()),
                conditionViews(QuestConditionKind.ACCEPTANCE, safeConditions, conditionResolver),
                conditionViews(QuestConditionKind.COMPLETION, safeConditions, conditionResolver),
                objectives,
                rewards,
                acceptEnabled,
                updatedAt
        );
    }

    private static List<PacketQuestScreenSnapshot.ConditionView> conditionViews(
            QuestConditionKind kind,
            QuestConditionOverview overview,
            @Nullable Function<QuestConditionKind, List<QuestConditionCheck>> resolver) {
        List<QuestConditionCheck> checks = resolver != null ? resolver.apply(kind) : List.of();
        if (checks != null && !checks.isEmpty()) {
            return checks.stream()
                    .filter(check -> check != null && !check.displayText().isBlank())
                    .map(check -> new PacketQuestScreenSnapshot.ConditionView(
                            check.displayText(), check.evaluated(), check.allowed()))
                    .toList();
        }
        return overview.texts(kind).stream()
                .map(text -> new PacketQuestScreenSnapshot.ConditionView(text, false, false))
                .toList();
    }
}
