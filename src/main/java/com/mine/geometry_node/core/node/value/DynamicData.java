package com.mine.geometry_node.core.node.value;

/**
 * [双模数字]
 * 数学节点的真实输出类型，兼顾服务端的单帧浮点与客户端的连续公式
 */
public record DynamicData(
        Object value,                // 原始值 (Vec3, Float, Boolean 等)
        ExpressionData expression    // 对应的动态公式协议
) {}