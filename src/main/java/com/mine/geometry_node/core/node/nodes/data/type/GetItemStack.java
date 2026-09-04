package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

public class GetItemStack extends BaseNode {

    public static final String TYPE_ID = "get_item_stack";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_item_stack"))
                // 1. 输出：具体的物品实例 (ItemStack)
                .addRow(new PortRow(null, StandardPorts.ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))

                // 2. 输入：物品类型图纸 (Item) - 默认苹果，并自带全物品下拉框！
                .addPassthroughInput(StandardPorts.ITEM.toInput("minecraft:apple"), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllItems().toArray(new String[0])))

                // 3. 输入：数量 (Count) - 默认 1
                .addPassthroughInput(StandardPorts.COUNT.toInput(1), UIHint.INPUT, null, null)
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        // 数据节点只响应它自己的输出端口
        if (!StandardPorts.ITEM_STACK.getId().equals(portName)) return null;

        // TypeConverter resolves the authored registry ID through the shared conversion registry.
        Item item = getInput(context, StandardPorts.ITEM.getId(), Item.class);
        Integer count = getInput(context, StandardPorts.COUNT.getId(), Integer.class);

        // 防呆：数量如果不合法，默认给 1 个
        if (count == null || count <= 0) count = 1;

        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(item, count);
    }
}
