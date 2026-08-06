package com.mine.geometry_node.core.engine.quest.model;

/** One independently evaluated condition projected into the quest screen. */
public record QuestConditionCheck(String displayText, boolean evaluated, boolean allowed) {
    public QuestConditionCheck {
        displayText = displayText == null ? "" : displayText.trim();
    }
}
