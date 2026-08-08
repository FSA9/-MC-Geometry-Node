package com.mine.geometry_node.core.engine.system.quest.model;

import org.jetbrains.annotations.Nullable;

public record QuestOperationResult(Code code, @Nullable QuestInstance instance, String message) {
    public QuestOperationResult {
        message = message == null ? "" : message;
        if (message.length() > 4096) {
            message = message.substring(0, 4096);
        }
    }

    public enum Code {
        SUCCESS,
        NO_CHANGE,
        INVALID_OWNER,
        INVALID_TASK_KEY,
        TASK_NOT_FOUND,
        NOT_A_QUEST_GRAPH,
        NOT_ACCEPTABLE,
        CONDITION_NOT_MET,
        CONDITION_EVALUATION_FAILED,
        COMPLETION_CONDITION_NOT_MET,
        COMPLETION_CONDITION_EVALUATION_FAILED,
        NOT_SUBMITTABLE,
        NOT_ABANDONABLE,
        ALREADY_EXISTS,
        INSTANCE_NOT_FOUND,
        INVALID_STATUS,
        INVALID_COUNTER_KEY,
        INVALID_COUNTER_VALUE
    }

    public boolean successful() {
        return code == Code.SUCCESS || code == Code.NO_CHANGE;
    }

    public static QuestOperationResult of(Code code) {
        return new QuestOperationResult(code, null, "");
    }

    public static QuestOperationResult of(Code code, @Nullable QuestInstance instance) {
        return new QuestOperationResult(code, instance, "");
    }

    public static QuestOperationResult failure(Code code, String message) {
        return new QuestOperationResult(code, null, message);
    }
}
