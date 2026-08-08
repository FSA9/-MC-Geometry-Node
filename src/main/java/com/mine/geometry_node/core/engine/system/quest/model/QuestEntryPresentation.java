package com.mine.geometry_node.core.engine.system.quest.model;

import com.google.gson.JsonObject;
import com.mine.geometry_node.core.node.value.RichTextValue;

import java.util.UUID;

public record QuestEntryPresentation(String entryId,
                                     RichTextValue content,
                                     QuestHintType hintType,
                                     String hintValue) {
    public QuestEntryPresentation {
        entryId = entryId != null ? entryId.trim() : "";
        if (entryId.isEmpty()) entryId = UUID.randomUUID().toString();
        content = content != null ? content : RichTextValue.EMPTY;
        hintType = hintType != null ? hintType : QuestHintType.NONE;
        hintValue = hintValue != null ? hintValue.trim() : "";
        if (hintType == QuestHintType.NONE) hintValue = "";
    }

    public static QuestEntryPresentation empty() {
        return new QuestEntryPresentation("", RichTextValue.EMPTY, QuestHintType.NONE, "");
    }

    public void writeTo(JsonObject root) {
        root.addProperty("entry_id", entryId);
        root.add("content", QuestDefinition.richTextToJson(content));
        root.addProperty("hint_type", hintType.id());
        if (hintType != QuestHintType.NONE && !hintValue.isEmpty()) {
            root.addProperty("hint_value", hintValue);
        }
    }

    public static QuestEntryPresentation fromJson(JsonObject root) {
        return new QuestEntryPresentation(
                QuestDefinition.readString(root, "entry_id", ""),
                QuestDefinition.readRichText(root.get("content")),
                QuestHintType.fromId(QuestDefinition.readString(root, "hint_type", "")),
                QuestDefinition.readString(root, "hint_value", ""));
    }
}
