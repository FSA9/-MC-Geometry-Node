package com.mine.geometry_node.core.node.value;

/** One ordered, passively evaluated condition exposed to a quest-condition sink. */
public record QuestConditionValue(String displayText, boolean allowed, String failureText) {
    public QuestConditionValue {
        displayText = normalize(displayText);
        failureText = normalize(failureText);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
