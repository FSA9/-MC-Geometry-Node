package com.mine.geometry_node.core.node.nodes.data.inventory;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.SlotAccessUtils;
import com.mine.geometry_node.core.node.util.ValueMatchUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

public class CountInventoryItem extends BaseNode {
    public static final String TYPE_ID = "count_inventory_item";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                统计目标主要物品存储中匹配条件的物品数量。
                玩家会扫描背包，容器会扫描容器槽位，其他实体会尝试扫描 NeoForge 物品能力。
                tag 非空时优先按 tag 匹配，否则按 ItemStack 和匹配模式匹配。""";

        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.count_inventory_item"))
                .comment(comment)
                .addRow(new PortRow(StandardPorts.ENTITY.toInput(), StandardPorts.COUNT.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.ITEM_STACK.toInput(), StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TAG.toInput(""), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(
                        StandardPorts.MATCH_MODE.toInput(ValueMatchUtils.MODE_COMPONENTS).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, ValueMatchUtils.MODE_OPTIONS)
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        Entity entity = getInput(context, StandardPorts.ENTITY.getId(), Entity.class);
        ItemStack template = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        String tag = getInput(context, StandardPorts.TAG.getId(), String.class);
        String matchMode = getInput(context, StandardPorts.MATCH_MODE.getId(), String.class);
        int count = SlotAccessUtils.countMatching(entity, template, tag, matchMode, context);

        if (StandardPorts.COUNT.getId().equals(portName)) {
            return count;
        }
        if (StandardPorts.BOOL.getId().equals(portName)) {
            return count > 0;
        }
        return null;
    }
}
