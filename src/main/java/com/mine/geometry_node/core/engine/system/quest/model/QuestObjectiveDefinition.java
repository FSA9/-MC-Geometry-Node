package com.mine.geometry_node.core.engine.system.quest.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.value.RichTextValue;
import org.jetbrains.annotations.Nullable;

public record QuestObjectiveDefinition(
        QuestEntryPresentation presentation,
        QuestCounterBinding counter,
        boolean quantityEnabled,
        double targetValue
) {
    public QuestObjectiveDefinition {
        presentation = presentation != null ? presentation : QuestEntryPresentation.empty();
        counter = counter != null ? counter : QuestCounterBinding.NONE;
        quantityEnabled = quantityEnabled || counter.enabled();
        targetValue = Double.isFinite(targetValue) ? Math.max(0.0, targetValue) : 0.0;
    }

    public QuestObjectiveDefinition(String entryId,
                                    RichTextValue content,
                                    boolean counterEnabled,
                                    String counterKey,
                                    boolean quantityEnabled,
                                    double targetValue,
                                    QuestHintType hintType,
                                    String hintValue) {
        this(new QuestEntryPresentation(entryId, content, hintType, hintValue),
                new QuestCounterBinding(counterEnabled, counterKey),
                quantityEnabled,
                targetValue);
    }

    public static QuestObjectiveDefinition empty() {
        return new QuestObjectiveDefinition(
                QuestEntryPresentation.empty(), QuestCounterBinding.NONE, false, 1.0);
    }

    public String entryId() {
        return presentation.entryId();
    }

    public RichTextValue content() {
        return presentation.content();
    }

    public boolean counterEnabled() {
        return counter.enabled();
    }

    public String counterKey() {
        return counter.key();
    }

    public QuestHintType hintType() {
        return presentation.hintType();
    }

    public String hintValue() {
        return presentation.hintValue();
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        presentation.writeTo(root);
        counter.writeTo(root);
        root.addProperty("quantity_enabled", quantityEnabled);
        if (quantityEnabled) root.addProperty("target_value", targetValue);
        return root;
    }

    public static QuestObjectiveDefinition fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return empty();
        JsonObject root = element.getAsJsonObject();
        QuestCounterBinding counter = QuestCounterBinding.fromJson(root);
        boolean quantityEnabled = QuestDefinition.readBoolean(
                root, "quantity_enabled", counter.enabled() || root.has("target_value"));
        return new QuestObjectiveDefinition(
                QuestEntryPresentation.fromJson(root),
                counter,
                quantityEnabled,
                QuestDefinition.readDouble(root, "target_value", 1.0));
    }
}
