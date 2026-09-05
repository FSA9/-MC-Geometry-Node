package com.mine.geometry_node.core.node.definition.port;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * [核心基建] 类型转换中心
 * 将类类型调用适配到 {@link PortConversionRegistry}。动态表达式包装会在
 * 此边界被透明解包；表达式元数据只能通过节点的专用内部接口读取。
 */
public class TypeConverter {

    /**
     * 核心转换方法。
     * @param val  原始数据
     * @param type 期望的目标类型
     * @param ctx  执行上下文，供需要运行时环境的已注册转换使用
     * @return 转换后的对象，如果完全无法转换则返回 null
     */
    @Nullable
    public static <T> T convert(@Nullable Object val, Class<T> type, GraphDataContext ctx) {
        if (val == null || type == null) return null;

        if (val instanceof DynamicData dyn) {
            val = dyn.value();
        }

        PortType target = PortValueTypeRegistry.forJavaClass(type);
        if (target == null) {
            return type.isInstance(val) ? type.cast(val) : null;
        }

        Object converted = PortConversionRegistry.convert(val, target, ctx);
        if (converted == null) return null;
        if (type == Double.class && converted instanceof Number number) {
            return type.cast(number.doubleValue());
        }
        return type.isInstance(converted) ? type.cast(converted) : null;
    }

    /**
     * Converts one indexed element. Scalars are treated as a one-element list;
     * invalid indexes and values resolve to {@code null} without compaction.
     */
    @Nullable
    public static <T> T convertFromList(@Nullable Object value, int index, Class<T> elementType,
                                        GraphDataContext context) {
        if (index < 0 || value == null) return null;
        if (value instanceof DynamicData dynamic) value = dynamic.value();

        Object element;
        if (value instanceof List<?> list) {
            if (index >= list.size()) return null;
            element = list.get(index);
        } else {
            if (index != 0) return null;
            element = value;
        }
        return convert(element, elementType, context);
    }

    /**
     * Converts a list while retaining null and rejected positions. A scalar is
     * treated as a one-element list so action inputs can accept one or many values.
     */
    public static <T> List<T> convertList(@Nullable Object value, Class<T> elementType,
                                          GraphDataContext context) {
        if (value == null) return new ArrayList<>();
        if (value instanceof DynamicData dynamic) value = dynamic.value();

        if (!(value instanceof List<?> list)) {
            T converted = convert(value, elementType, context);
            List<T> result = new ArrayList<>(1);
            result.add(converted);
            return result;
        }

        List<T> result = new ArrayList<>(list.size());
        for (Object element : list) {
            result.add(convert(element, elementType, context));
        }
        return result;
    }

    /** Returns a graph DICT containing only its string-keyed entries. */
    public static Map<String, Object> convertStringMap(@Nullable Object value,
                                                        GraphDataContext context) {
        Map<?, ?> map = convert(value, Map.class, context);
        if (map == null) return Collections.emptyMap();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() instanceof String key) {
                result.put(key, entry.getValue());
            }
        }
        return result;
    }

    /**
     * Converts a value to the canonical in-memory representation of a port type.
     * Context-free callers, such as graph compilers, use the overload without a context.
     */
    @Nullable
    public static Object convertForPort(@Nullable Object value, PortType type,
                                        @Nullable GraphDataContext context) {
        if (value instanceof DynamicData dynamic) {
            value = dynamic.value();
        }
        Object converted = PortConversionRegistry.convert(value, type, context);
        return converted;
    }

    @Nullable
    public static Object convertForPort(@Nullable Object value, PortType type) {
        return convertForPort(value, type, null);
    }

}
