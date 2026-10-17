package com.mine.geometry_node.client.ui.editor.graph.sidebar.properties.quest;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.ui.editor.graph.properties.GraphPropertiesTarget;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestEntryPresentation;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import com.mine.geometry_node.core.engine.system.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestRewardDefinition;
import icyllis.modernui.mc.MuiModApi;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** A picker result belongs to a document entry, even if opening a native screen destroys its controls. */
final class QuestHintSelection {
    enum EntryKind { OBJECTIVE, REWARD }

    private QuestHintSelection() {}

    static BiConsumer<QuestHintType, String> capture(GraphPropertiesTarget target, String entryId, EntryKind kind) {
        return capture(target, entryId, kind, MuiModApi::postToUiThread);
    }

    static BiConsumer<QuestHintType, String> capture(GraphPropertiesTarget target, String entryId,
                                                    EntryKind kind, Executor uiExecutor) {
        return (type, value) -> {
            if (target == null) return;
            target.load().thenComposeAsync(snapshot -> {
                if (snapshot == null) return CompletableFuture.completedFuture(null);
                QuestDefinition previous = snapshot.questDefinition();
                QuestDefinition updated = withHint(previous, entryId, kind, type, value);
                if (updated.equals(previous)) return CompletableFuture.completedFuture(null);
                var pending = snapshot.withMetadata(snapshot.graphTypeId(), snapshot.comment(), snapshot.tags(), updated);
                return target.save(pending.graphTypeId(), pending.comment(), pending.tags(), updated)
                        .thenRun(() -> target.onSaveSucceeded(pending));
            }, uiExecutor).whenComplete((ignored, error) -> {
                if (error != null) GeometryNode.LOGGER.error("Failed to save selected quest hint: {}", entryId, error);
            });
        };
    }

    private static QuestDefinition withHint(QuestDefinition definition, String entryId, EntryKind kind,
                                            QuestHintType type, String value) {
        if (kind == EntryKind.OBJECTIVE) {
            var objectives = definition.objectives().stream().map(entry -> {
                if (!entry.entryId().equals(entryId)) return entry;
                return new QuestObjectiveDefinition(
                        new QuestEntryPresentation(entryId, entry.content(), type, value),
                        entry.counter(), entry.quantityEnabled(), entry.targetValue());
            }).toList();
            return new QuestDefinition(definition.title(), definition.description(), objectives, definition.rewards());
        }
        var rewards = definition.rewards().stream().map(entry -> {
            if (!entry.entryId().equals(entryId)) return entry;
            return new QuestRewardDefinition(
                    new QuestEntryPresentation(entryId, entry.content(), type, value), entry.counter(), entry.amount());
        }).toList();
        return new QuestDefinition(definition.title(), definition.description(), definition.objectives(), rewards);
    }
}
