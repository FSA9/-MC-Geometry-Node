package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.runtime.dialogue.ModernDialogueText;
import com.mine.geometry_node.core.node.value.RichTextValue;

/** Reuses the existing ModernUI rich-text renderer, including its plain-text fallback. */
public final class FactionTextViews {
    private FactionTextViews() {}
    public static CharSequence display(RichTextValue value) { return ModernDialogueText.display(value); }
}
