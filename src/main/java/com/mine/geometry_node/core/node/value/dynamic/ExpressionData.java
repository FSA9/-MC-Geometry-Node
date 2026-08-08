package com.mine.geometry_node.core.node.value.dynamic;

import java.util.Map;

/**
 * [公式协议载体]
 * 描述一段可被客户端执行的动态数据
 */
public record ExpressionData(
        String formula,              // 公式字符串，如 "(sin(T)) * A"
        Map<String, String> bindings // 变量绑定关系，如 "A" -> "entity:123:speed"
) {
    public static final ExpressionData ZERO = new ExpressionData("0", java.util.Collections.emptyMap());
}