package com.mine.geometry_node.client.ui.viewport.node.UIHints;

import com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers.*;
import com.mine.geometry_node.core.node.port.UIHint;
import java.util.EnumMap;
import java.util.Map;

public class HintRendererFactory {
    private static final Map<UIHint, UIHintRenderer> RENDERERS = new EnumMap<>(UIHint.class);

    static {
        RENDERERS.put(UIHint.CHECKBOX, new CheckBoxHintRenderer());
        RENDERERS.put(UIHint.INPUT, new InputHintRenderer());
        RENDERERS.put(UIHint.SELECT, new SelectHintRenderer());
        RENDERERS.put(UIHint.VECTOR, new VectorHintRenderer());
        RENDERERS.put(UIHint.BUTTON, new ButtonHintRenderer());
        RENDERERS.put(UIHint.ITEM_SLOT, new ItemSlotHintRenderer());
    }

    public static UIHintRenderer getRenderer(UIHint hint) {
        return RENDERERS.get(hint);
    }
}
