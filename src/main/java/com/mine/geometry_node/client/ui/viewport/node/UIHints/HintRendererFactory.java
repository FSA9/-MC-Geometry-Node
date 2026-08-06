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
        RENDERERS.put(UIHint.PATH, new PathHintRenderer());
        RENDERERS.put(UIHint.SELECT, new SelectHintRenderer());
        RENDERERS.put(UIHint.VECTOR, new VectorHintRenderer());
        RENDERERS.put(UIHint.BUTTON, new ButtonHintRenderer());
        RENDERERS.put(UIHint.ITEM_SLOT, new ItemSlotHintRenderer());
        RENDERERS.put(UIHint.ENTITY_TEMPLATE, new EntityTemplateHintRenderer());
        RENDERERS.put(UIHint.SLOT_REF, new SlotRefHintRenderer());
        RENDERERS.put(UIHint.CUSTOM, new CustomHintRenderer());
    }

    public static UIHintRenderer getRenderer(UIHint hint) {
        return RENDERERS.get(hint);
    }
}
