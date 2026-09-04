package com.mine.geometry_node.core.node.definition.port;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * [核心基建] 类型转换中心
 * 将类类型调用适配到 {@link PortConversionRegistry}。除表达式包装协议外，
 * 此类不再自行声明隐式转换规则。
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
            if (type == ExpressionData.class) {
                return type.cast(dyn.expression());
            }
            val = dyn.value();
        } else if (type == ExpressionData.class && val instanceof Number num) {
            return type.cast(new ExpressionData(String.valueOf(num.floatValue()), java.util.Map.of()));
        }

        if (type == ExpressionData.class && (val instanceof List || val instanceof Vec3)) {
            return null;
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
     * Converts a value to the canonical in-memory representation of a port type.
     * Context-free callers, such as graph compilers, use the overload without a context.
     */
    @Nullable
    public static Object convertForPort(@Nullable Object value, PortType type,
                                        @Nullable GraphDataContext context) {
        Object converted = PortConversionRegistry.convert(value, type, context);
        return converted;
    }

    @Nullable
    public static Object convertForPort(@Nullable Object value, PortType type) {
        return convertForPort(value, type, null);
    }

}
