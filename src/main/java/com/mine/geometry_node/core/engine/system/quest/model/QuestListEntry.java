package com.mine.geometry_node.core.engine.system.quest.model;

import net.minecraft.nbt.CompoundTag;

import java.util.Objects;

public record QuestListEntry(String taskKey,
                             boolean visible,
                             boolean acceptEnabled,
                             String sourceId,
                             long publishedAt) {
    public QuestListEntry {
        taskKey = Objects.requireNonNullElse(taskKey, "").trim();
        sourceId = Objects.requireNonNullElse(sourceId, "").trim();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("TaskKey", taskKey);
        tag.putBoolean("Visible", visible);
        tag.putBoolean("AcceptEnabled", acceptEnabled);
        if (!sourceId.isEmpty()) {
            tag.putString("SourceId", sourceId);
        }
        tag.putLong("PublishedAt", publishedAt);
        return tag;
    }

    public static QuestListEntry load(CompoundTag tag) {
        return new QuestListEntry(
                tag.getStringOr("TaskKey", ""),
                tag.getBooleanOr("Visible", false),
                tag.getBooleanOr("AcceptEnabled", true),
                tag.getStringOr("SourceId", ""),
                tag.getLongOr("PublishedAt", 0L)
        );
    }
}
