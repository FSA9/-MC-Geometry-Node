package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
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
                .addRow(new PortRow(
                        StandardPorts.ITEM.toInput("minecraft:apple"),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllItems().toArray(new String[0]))
                ))

                // 3. 输入：数量 (Count) - 默认 1
                .addRow(new PortRow(StandardPorts.COUNT.toInput(1), null, UIHint.INPUT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 数据节点只响应它自己的输出端口
        if (!StandardPorts.ITEM_STACK.getId().equals(portName)) return null;

        // 1. 获取物品图纸 (可能是 String 也可能是 Item 对象) 和数量
        Object rawItem = getRawInput(context, StandardPorts.ITEM.getId());
        Integer count = getInput(context, StandardPorts.COUNT.getId(), Integer.class);

        // 防呆：数量如果不合法，默认给 1 个
        if (count == null || count <= 0) count = 1;

        Item item = Items.AIR;

        // 2. 智能解析物品图纸 (因为 PortType.ITEM 允许接收 STRING)
        if (rawItem instanceof Item i) {
            item = i;
        } else if (rawItem instanceof String s) {
            Identifier loc = Identifier.tryParse(s);
            if (loc != null) {
                // 在 1.21 中，从注册表安全获取 Item
                item = BuiltInRegistries.ITEM.getOptional(loc).orElse(Items.AIR);
            }
        }

        // 如果最终解析出来是空气（比如玩家乱填了一个不存在的 ID），直接返回空物品栈
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        // 3. 核心：将抽象的图纸，实例化为内存中具体的物品栈对象！
        return new ItemStack(item, count);
    }
}