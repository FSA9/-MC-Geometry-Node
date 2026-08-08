package com.mine.geometry_node.core.engine.system.quest.status;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class QuestStatusRegistry {
    public static final String DYNAMIC_REGISTRY_ID = "geometry_node:quest_statuses";
    public static final String ASSIGNABLE_DYNAMIC_REGISTRY_ID = "geometry_node:assignable_quest_statuses";

    public static final QuestStatus UNACCEPTED = new QuestStatus(
            "unaccepted", "geometry_node.quest.status.unaccepted", false, false, false, true, 0xFF9AA0A6);
    public static final QuestStatus IN_PROGRESS = new QuestStatus(
            "in_progress", "geometry_node.quest.status.in_progress", false, true, true, true, 0xFF4DA3FF);
    public static final QuestStatus COMPLETED = new QuestStatus(
            "completed", "geometry_node.quest.status.completed", true, false, true, true, 0xFF55B96B);
    public static final QuestStatus FAILED = new QuestStatus(
            "failed", "geometry_node.quest.status.failed", true, false, true, true, 0xFFE05A5A);
    public static final QuestStatus ABANDONED = new QuestStatus(
            "abandoned", "geometry_node.quest.status.abandoned", true, false, true, true, 0xFFF0A14A);
    public static final QuestStatus MISSED = new QuestStatus(
            "missed", "geometry_node.quest.status.missed", true, false, false, true, 0xFF9B7EDB);
    public static final QuestStatusRegistry INSTANCE = new QuestStatusRegistry();

    private final Map<String, QuestStatus> statuses = new LinkedHashMap<>();

    private QuestStatusRegistry() {
        register(UNACCEPTED);
        register(IN_PROGRESS);
        register(COMPLETED);
        register(FAILED);
        register(ABANDONED);
        register(MISSED);
    }

    public synchronized void register(QuestStatus status) {
        if (status == null) return;
        QuestStatus existing = statuses.get(status.id());
        if (existing != null && !existing.equals(status)) {
            throw new IllegalStateException("Duplicate quest status: " + status.id());
        }
        statuses.put(status.id(), status);
    }

    @Nullable
    public synchronized QuestStatus get(@Nullable String id) {
        return statuses.get(QuestStatus.normalizeId(id));
    }

    public synchronized Collection<QuestStatus> all() {
        return Collections.unmodifiableList(new ArrayList<>(statuses.values()));
    }

    public synchronized Collection<QuestStatus> assignable() {
        return Collections.unmodifiableList(statuses.values().stream()
                .filter(QuestStatus::assignable)
                .toList());
    }

    public synchronized List<String> allIds() {
        return statuses.values().stream().map(QuestStatus::id).toList();
    }

    public synchronized List<String> assignableIds() {
        return statuses.values().stream()
                .filter(QuestStatus::assignable)
                .map(QuestStatus::id)
                .toList();
    }
}
