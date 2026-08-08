package com.mine.geometry_node.core.engine.system.quest.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.value.RichTextValue;
import org.jetbrains.annotations.Nullable;

public record QuestRewardDefinition(
        QuestEntryPresentation presentation,
        QuestCounterBinding counter,
        double amount
) {
    public QuestRewardDefinition {
        presentation = presentation != null ? presentation : QuestEntryPresentation.empty();
        counter = counter != null ? counter : QuestCounterBinding.NONE;
        amount = Double.isFinite(amount) ? Math.max(0.0, amount) : 0.0;
    }

    public QuestRewardDefinition(String entryId,
                                 RichTextValue content,
                                 boolean counterEnabled,
                                 String counterKey,
                                 double amount,
                                 QuestHintType hintType,
                                 String hintValue) {
        this(new QuestEntryPresentation(entryId, content, hintType, hintValue),
                new QuestCounterBinding(counterEnabled, counterKey),
                amount);
    }

    public static QuestRewardDefinition empty() {
        return new QuestRewardDefinition(
                QuestEntryPresentation.empty(), QuestCounterBinding.NONE, 1.0);
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
        root.addProperty("amount", amount);
        return root;
    }

    public static QuestRewardDefinition fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonObject()) return empty();
        JsonObject root = element.getAsJsonObject();
        return new QuestRewardDefinition(
                QuestEntryPresentation.fromJson(root),
                QuestCounterBinding.fromJson(root),
                QuestDefinition.readDouble(root, "amount", 1.0));
    }
}
