package com.mine.geometry_node.core.node.nodes.maths;

import com.mine.geometry_node.core.engine.blueprint.execution.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.*;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import net.minecraft.network.chat.Component;

public class RandomValue extends BaseNode {

    public static final String TYPE_ID = "random_value";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.MATH, Component.translatable("geometry_node.node.random_value"))
                // 第一行：输出端口 (随机生成的结果)
                .addRow(new PortRow(
                        null,
                        StandardPorts.VALUE.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                // 第二行：输入端口1 - 最小值 (带有默认值 0.0f，并支持UI直接输入)
                .addRow(new PortRow(
                        StandardPorts.VALUE.toInputWithIndex(1, 0.0f),
                        null,
                        UIHint.INPUT, null, null
                ))
                // 第三行：输入端口2 - 最大值 (带有默认值 1.0f，并支持UI直接输入)
                .addRow(new PortRow(
                        StandardPorts.VALUE.toInputWithIndex(2, 1.0f),
                        null,
                        UIHint.INPUT, null, null
                ))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        // 如果请求的不是 VALUE 端口，直接跳过
        if (!StandardPorts.VALUE.getId().equals(portName)) {
            return null;
        }

        // 获取连线输入或 UI 静态输入的 Min 和 Max
        Float min = getInput(context, StandardPorts.VALUE.getIdWithIndex(1), Float.class);
        Float max = getInput(context, StandardPorts.VALUE.getIdWithIndex(2), Float.class);

        // 兜底保护
        float minVal = min != null ? min : 0.0f;
        float maxVal = max != null ? max : 1.0f;

        // 自动纠正大小关系，防止玩家在输入框里填反了 (比如 min=5, max=2)
        if (minVal > maxVal) {
            float temp = minVal;
            minVal = maxVal;
            maxVal = temp;
        }

        // 计算并返回 [minVal, maxVal) 之间的随机浮点数
        return minVal + (float) Math.random() * (maxVal - minVal);
    }
}