package com.mine.geometry_node.client.ui.components.integration.graph.properties.quest;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.integration.picker.VanillaInventoryPicker;
import com.mine.geometry_node.client.ui.components.integration.graph.properties.GraphPropertiesTarget;
import com.mine.geometry_node.core.engine.system.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestEntryPresentation;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import com.mine.geometry_node.core.engine.system.quest.model.QuestObjectiveDefinition;
import com.mine.geometry_node.core.engine.system.quest.model.QuestRewardDefinition;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.mc.MuiModApi;
import net.minecraft.client.Minecraft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;

/** A picker result belongs to a document entry, even if opening a native screen destroys its controls. */
final class QuestHintSelection {
    enum EntryKind { OBJECTIVE, REWARD }

    private QuestHintSelection() {}

    static void pickItem(GraphPropertiesTarget target, String entryId, EntryKind kind, Runnable onReturn) {
        var selection = capture(target, entryId, kind);
        VanillaInventoryPicker.openItem(stack -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) return;
            selection.accept(QuestHintType.ITEM_STACK,
                    ItemCodecUtils.toJson(stack, minecraft.level.registryAccess()));
        }, onReturn);
    }

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
                        .thenRunAsync(() -> target.onSaveSucceeded(pending), uiExecutor);
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
            return new QuestDefinition(definition.title(), definition.description(), objectives, definition.rewards(),
                    definition.visibilityConditions(), definition.acceptanceConditions(), definition.completionConditions());
        }
        var rewards = definition.rewards().stream().map(entry -> {
            if (!entry.entryId().equals(entryId)) return entry;
            return new QuestRewardDefinition(
                    new QuestEntryPresentation(entryId, entry.content(), type, value), entry.counter(), entry.amount());
        }).toList();
        return new QuestDefinition(definition.title(), definition.description(), definition.objectives(), rewards,
                definition.visibilityConditions(), definition.acceptanceConditions(), definition.completionConditions());
    }
}
