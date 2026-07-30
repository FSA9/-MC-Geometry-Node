package com.mine.geometry_node.core.node.value;

public record DialogueChoiceValue(
        RichTextValue text,
        boolean visible,
        boolean enabled,
        RichTextValue disabledReason
) {
    public DialogueChoiceValue {
        text = text == null ? RichTextValue.EMPTY : text;
        disabledReason = disabledReason == null ? RichTextValue.EMPTY : disabledReason;
    }

    public boolean isValid() {
        return !text.plain().isBlank();
    }
}
