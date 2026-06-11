package com.mine.geometry_node.core.engine.dialogue.richtext;

import net.minecraft.network.chat.Component;

/**
 * Normalized dialogue text after parsing and safety filtering.
 */
public record DialogueRichText(String plainText, Component component) {

    public DialogueRichText {
        plainText = plainText == null ? "" : plainText;
        component = component == null ? Component.literal(plainText) : component;
    }
}
