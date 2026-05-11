package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public class Equal extends BaseNode {

    public static final String TYPE_ID = "equal";

    @Override
    public NodeDef getDefaultDefinition() {
//        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.equal"))
//                // 第一行：左侧输入 A，右侧输出结果 BOOL
//                .addRow(new PortRow(null, StandardPorts.BOOL.toOutput(), UIHint.DEFAULT, null, null))
//                // 第二行：左侧输入 B
//                .addRow(new PortRow(new PortDef("B", Component.literal("B"), PortType.ANY, null), null, UIHint.DEFAULT, null, null))
//                .build();
        return null;
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) return null;

        Object a = getRawInput(context, "A");
        Object b = getRawInput(context, "B");

        // 1. 基础空值防御
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        // 2. 数学模式：数字比较 (忽略数据类型，只看数值大小。20 == 20.0f -> true)
        if (a instanceof Number numA && b instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue()) == 0;
        }

        // 3. 实体模式：比较实体 (通过 UUID 防御实体重新加载导致的内存引用变化)
        if (a instanceof Entity entA && b instanceof Entity entB) {
            return entA.getUUID().equals(entB.getUUID());
        }

        // 4. 物品模式：比较物品栈 (忽略数量堆叠大小，但极其严格地检查物品类型和数据组件 NBT)
        if (a instanceof ItemStack stackA && b instanceof ItemStack stackB) {
            return ItemStack.isSameItemSameComponents(stackA, stackB);
        }

        // 5. 默认模式：方块状态(BlockState)、字典(Map)、列表(List)、字符串(String)、布尔(Boolean)
        // 这些类型在 Java 和 Minecraft 底层都拥有极其完美的 equals() 深度比较逻辑
        return a.equals(b);
    }
}