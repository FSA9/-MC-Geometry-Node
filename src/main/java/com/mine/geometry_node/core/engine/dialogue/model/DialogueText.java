package com.mine.geometry_node.core.engine.dialogue.model;

import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.chat.Component;

/**
 * Explicit text representation used by dialogue payloads.
 */
public sealed interface DialogueText permits DialogueText.Plain, DialogueText.ComponentText, DialogueText.Rich {
    DialogueText EMPTY = new Plain("");

    static DialogueText plain(String value) {
        return value == null || value.isEmpty() ? EMPTY : new Plain(value);
    }

    static DialogueText component(Component value) {
        return value == null ? EMPTY : new ComponentText(value);
    }

    static DialogueText rich(RichTextValue value) {
        return value == null || value.plain().isEmpty() ? EMPTY : new Rich(value);
    }

    record Plain(String value) implements DialogueText {
        public Plain {
            value = value == null ? "" : value;
        }
    }

    record ComponentText(Component value) implements DialogueText {
        public ComponentText {
            value = value == null ? Component.empty() : value.copy();
        }

        @Override
        public Component value() {
            return value.copy();
        }
    }

    record Rich(RichTextValue value) implements DialogueText {
        public Rich {
            value = value == null ? RichTextValue.EMPTY : value;
        }
    }
}
