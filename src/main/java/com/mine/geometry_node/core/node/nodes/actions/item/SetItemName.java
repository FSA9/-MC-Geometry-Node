package com.mine.geometry_node.core.node.nodes.actions.item;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

public class SetItemName extends BaseNode {

    public static final String TYPE_ID = "set_item_name";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, net.minecraft.network.chat.Component.translatable("geometry_node.node.set_item_name"))
                // 1. 执行流：进 -> 出
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))

                // 2. 数据流直通：左侧接收物品，右侧输出修改后的同一件物品
                .addRow(new PortRow(null, StandardPorts.RESULT_ITEM_STACK.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.ITEM_STACK.toInput(), UIHint.DEFAULT)

                // 3. 配置项：要修改的名字
                .addPassthroughInput(StandardPorts.NAME.toInput("神兵利器"), UIHint.INPUT)
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), null);
        // 1. 获取上游传来的物品实例
        ItemStack stack = getInput(context, StandardPorts.ITEM_STACK.getId(), ItemStack.class);
        String name = getInput(context, StandardPorts.NAME.getId(), String.class);

        // 2. 安全校验并修改组件 (Minecraft 1.21 现代写法)
        if (stack != null && !stack.isEmpty() && name != null && !name.isEmpty()) {
            // 使用 Component.literal 将字符串包装为富文本，并写入 CUSTOM_NAME 组件
            stack.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal(name));
        }

        // 3. 将修改后的物品栈存入临时缓存，供下游节点拉取
        if (stack != null) {
            context.setNodeResult(StandardPorts.RESULT_ITEM_STACK.getId(), stack);
        }

        // 4. 推动执行流到下一个节点
        return next(StandardPorts.FLOW_OUT.getId());
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 当下游节点需要 ITEM_STACK 时，直接把我们刚才修改好并存入缓存的对象给它
        if (StandardPorts.RESULT_ITEM_STACK.getId().equals(portName)) {
            return context.getNodeResult(StandardPorts.RESULT_ITEM_STACK.getId());
        }
        return null;
    }
}