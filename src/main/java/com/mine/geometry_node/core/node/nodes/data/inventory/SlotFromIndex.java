package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.NodeComment;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.SlotRef;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class SlotFromIndex extends BaseNode {
    public static final String TYPE_ID = "slot_from_index";

    private static final String SCOPE_PLAYER_INVENTORY = "player_inventory";
    private static final String SCOPE_HOTBAR = "hotbar";
    private static final String SCOPE_MAIN_INVENTORY = "main_inventory";
    private static final String SCOPE_EQUIPMENT = "equipment";
    private static final String SCOPE_CONTAINER = "container";
    private static final String SCOPE_ENTITY_ITEM_HANDLER = "entity_item_handler";
    private static final String[] SCOPE_OPTIONS = {
            SCOPE_PLAYER_INVENTORY,
            SCOPE_HOTBAR,
            SCOPE_MAIN_INVENTORY,
            SCOPE_EQUIPMENT,
            SCOPE_CONTAINER,
            SCOPE_ENTITY_ITEM_HANDLER
    };
    private static final String[] EQUIPMENT_KEYS = {"mainhand", "offhand", "head", "chest", "legs", "feet"};

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.slot_from_index"))
                .comment(NodeComment.builder(TYPE_ID)
                        .text("summary")
                        .output(StandardPorts.SLOT, "slot")
                        .input(StandardPorts.INDEX, "index")
                        .input(StandardPorts.SCOPE, "scope")
                        .build())
                .addRow(new PortRow(null, StandardPorts.SLOT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.INDEX.toInput(0), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.SCOPE.toInput(SCOPE_PLAYER_INVENTORY).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, SCOPE_OPTIONS)
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.SLOT.getId().equals(portName)) {
            return null;
        }
        Integer index = getInput(context, StandardPorts.INDEX.getId(), Integer.class);
        String scope = getInput(context, StandardPorts.SCOPE.getId(), String.class);
        int value = index != null ? index : 0;

        return switch (scope != null ? scope : SCOPE_PLAYER_INVENTORY) {
            case SCOPE_HOTBAR -> new SlotRef(SlotRef.PLAYER_INVENTORY, "hotbar." + Math.floorMod(value, 9));
            case SCOPE_MAIN_INVENTORY -> new SlotRef(SlotRef.PLAYER_INVENTORY, "main." + Math.floorMod(value, 27));
            case SCOPE_EQUIPMENT -> new SlotRef(SlotRef.EQUIPMENT, EQUIPMENT_KEYS[Math.floorMod(value, EQUIPMENT_KEYS.length)]);
            case SCOPE_CONTAINER -> new SlotRef(SlotRef.CONTAINER, "slot." + Math.max(0, value));
            case SCOPE_ENTITY_ITEM_HANDLER -> new SlotRef(SlotRef.ENTITY_ITEM_HANDLER, "slot." + Math.max(0, value));
            default -> new SlotRef(SlotRef.PLAYER_INVENTORY, "inventory." + Math.floorMod(value, 36));
        };
    }
}
