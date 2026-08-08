package com.mine.geometry_node.core.engine.system.quest.model;

import java.util.List;

public record QuestConditionResult(boolean evaluated, boolean allowed, List<String> failureReasons) {
    public QuestConditionResult {
        failureReasons = failureReasons == null
                ? List.of()
                : failureReasons.stream()
                .filter(reason -> reason != null && !reason.isBlank())
                .map(String::trim)
                .toList();
    }

    public static QuestConditionResult passed() {
        return new QuestConditionResult(true, true, List.of());
    }

    public static QuestConditionResult denied(List<String> reasons) {
        return new QuestConditionResult(true, false, reasons);
    }

    public static QuestConditionResult evaluationFailed() {
        return new QuestConditionResult(false, false, List.of());
    }

    public String firstFailureReason() {
        return failureReasons.isEmpty() ? "" : failureReasons.getFirst();
    }
}
