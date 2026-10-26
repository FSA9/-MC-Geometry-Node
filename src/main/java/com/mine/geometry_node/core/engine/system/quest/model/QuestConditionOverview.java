package com.mine.geometry_node.core.engine.system.quest.model;

import java.util.List;

/** Static, player-independent condition text projected into the graph properties panel. */
public record QuestConditionOverview(
        List<String> visibility,
        List<String> acceptance,
        List<String> completion
) {
    public static final QuestConditionOverview EMPTY = new QuestConditionOverview(
            List.of(), List.of(), List.of());

    public QuestConditionOverview {
        visibility = immutableText(visibility);
        acceptance = immutableText(acceptance);
        completion = immutableText(completion);
    }

    public List<String> texts(QuestConditionKind kind) {
        return switch (kind) {
            case VISIBILITY -> visibility;
            case ACCEPTANCE -> acceptance;
            case COMPLETION -> completion;
        };
    }

    private static List<String> immutableText(List<String> values) {
        if (values == null || values.isEmpty()) return List.of();
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }
}
