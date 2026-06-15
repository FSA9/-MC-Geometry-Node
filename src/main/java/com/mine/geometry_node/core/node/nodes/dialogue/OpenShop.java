package com.mine.geometry_node.core.node.nodes.dialogue;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.dialogue.context.DialogueContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenShop extends BaseNode {
    public static final String TYPE_ID = "open_shop";
    public static final String PLAYER = StandardPorts.PLAYER.getId();
    public static final String TITLE = StandardPorts.TITLE.getId();
    public static final String SHOP_DATA = StandardPorts.SHOP_DATA.getId();
    public static final String TEMP_SHOP_DATA = "open_shop_data";

    private static final String ACTION_OPEN_SHOP_EDITOR = "open_shop_editor";
    private static final Map<String, Object> DEFAULT_SHOP_DATA = Map.of("offers", List.of());

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DIALOGUE, Component.translatable("geometry_node.node.open_shop"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.PLAYER.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TITLE.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.SHOP_DATA.toInput(DEFAULT_SHOP_DATA).hiddenPin(),
                        null,
                        UIHint.BUTTON,
                        null,
                        Map.of(
                                PortMetaKeys.BUTTON_LABEL, "geometry_node.button.edit_shop",
                                PortMetaKeys.BUTTON_ACTION, ACTION_OPEN_SHOP_EDITOR,
                                PortMetaKeys.BUTTON_COLOR, 0xFF3D6EA8,
                                PortMetaKeys.BUTTON_TEXT_COLOR, 0xFFFFFFFF
                        )
                ))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        ServerPlayer player = resolvePlayer(context);
        Map<String, Object> shopData = getInputDict(context, SHOP_DATA);
        String title = getInput(context, TITLE, String.class);

        Map<String, Object> state = new HashMap<>();
        if (player != null) {
            state.put("player", player);
        }
        state.put("title", title == null ? "" : title);
        state.put("shop_data", shopData);
        context.setTempData(TEMP_SHOP_DATA, state);

        // The custom shop screen/session will hook into this node later. For now
        // the node establishes the saved data contract and keeps flow moving.
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private ServerPlayer resolvePlayer(ExecutionContext context) {
        Entity target = getInput(context, PLAYER, Entity.class);
        if (target instanceof ServerPlayer player) {
            return player;
        }
        Object dialogueContext = context.getTempData(DialogueContext.TEMP_KEY);
        if (dialogueContext instanceof DialogueContext contextValue && contextValue.player() != null) {
            return contextValue.player();
        }
        Object triggerEntity = context.getEventData(StandardPorts.TRIGGER_ENTITY.getId());
        if (triggerEntity instanceof ServerPlayer player) {
            return player;
        }
        Object eventEntity = context.getEventData(StandardPorts.ENTITY.getId());
        if (eventEntity instanceof ServerPlayer player) {
            return player;
        }
        Entity owner = context.getEntity();
        return owner instanceof ServerPlayer player ? player : null;
    }
}
