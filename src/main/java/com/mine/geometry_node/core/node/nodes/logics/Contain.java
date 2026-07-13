package com.mine.geometry_node.core.node.nodes.logics;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortDef;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.PortType;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.util.ValueMatchUtils;
import com.mine.geometry_node.core.node.util.ValueTagUtils;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Map;

public class Contain extends BaseNode {

    public static final String TYPE_ID = "contain";
    private static final String CONTAINER = "container";

    @Override
    public NodeDef getDefaultDefinition() {
        String comment = """
                判断一个值是否包含另一个值。
                字符串执行子串匹配，列表和字典执行元素匹配。
                当容器是物品、方块、实体类型等注册表对象且目标是 tag 字符串时，判断对象是否属于该 tag。""";

        return NodeDef.builder(TYPE_ID, NodeType.LOGIC, Component.translatable("geometry_node.node.contain"))
                .comment(comment)
                .addRow(new PortRow(
                        new PortDef(CONTAINER, Component.translatable("geometry_node.port.container"), PortType.ANY, null, false),
                        StandardPorts.BOOL.toOutput(),
                        UIHint.DEFAULT, null, null
                ))
                .addRow(new PortRow(StandardPorts.ANY_VALUE.toInput(), null, UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(ExecutionContext context, String portName) {
        if (!StandardPorts.BOOL.getId().equals(portName)) {
            return null;
        }

        Object container = getRawInput(context, CONTAINER);
        Object target = getRawInput(context, StandardPorts.ANY_VALUE.getId());
        return contains(container, target, context);
    }

    private static boolean contains(Object container, Object target, ExecutionContext context) {
        container = ValueTagUtils.unwrap(container);
        target = ValueTagUtils.unwrap(target);
        if (container == null || target == null) {
            return false;
        }

        if (container instanceof String text) {
            return text.contains(String.valueOf(target));
        }

        if (container instanceof Collection<?> collection) {
            for (Object value : collection) {
                if (valueMatches(value, target, context)) {
                    return true;
                }
            }
            return false;
        }

        if (container instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (valueMatches(entry.getKey(), target, context) || valueMatches(entry.getValue(), target, context)) {
                    return true;
                }
            }
            return false;
        }

        if (container.getClass().isArray()) {
            int length = Array.getLength(container);
            for (int i = 0; i < length; i++) {
                if (valueMatches(Array.get(container, i), target, context)) {
                    return true;
                }
            }
            return false;
        }

        if (target instanceof String tagId && ValueTagUtils.hasTag(container, tagId, context)) {
            return true;
        }

        return valueMatches(container, target, context);
    }

    private static boolean valueMatches(Object value, Object target, ExecutionContext context) {
        value = ValueTagUtils.unwrap(value);
        target = ValueTagUtils.unwrap(target);

        if (value instanceof String left && target instanceof String right && ValueTagUtils.tagStringsEqual(left, right)) {
            return true;
        }
        return ValueMatchUtils.valuesEqual(value, target, ValueMatchUtils.MODE_EXACT, ValueMatchUtils.COUNT_EXACT, context);
    }
}
