package com.mine.geometry_node.core.node.nodes;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionBinding;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.value.dynamic.DynamicData;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.node.definition.port.TypeConverter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * [逻辑定义层] 节点行为基类
 */
public abstract class BaseNode {

    public abstract NodeDef getDefaultDefinition();

    public NodeDef getDefinition(NodeData instanceData) {
        return getDefaultDefinition();
    }

    public final String getTypeId() {
        return getDefaultDefinition().typeId();
    }

    public ExecutionResult execute(ExecutionContext context) {
        return ExecutionResult.finish();
    }

    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        if (context instanceof ExecutionContext blueprintContext) {
            return compute(blueprintContext, portName);
        }
        return null;
    }

    /**
     * Legacy blueprint-specific data entry point. New pure data nodes should
     * override {@link #compute(GraphDataContext, String)} instead.
     */
    @Deprecated
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        return null;
    }

    // ==========================================
    // 核心数据泵
    // ==========================================

    @Nullable
    protected Object getRawInput(GraphDataContext ctx, String portName) {
        Object value = ctx.getInputValue(portName);
        return value != null || ctx.hasInputConnection(portName)
                ? value
                : ctx.getStaticInput(portName);
    }

    @Nullable
    protected <T> T getInput(GraphDataContext ctx, String portName, Class<T> type) {
        Object raw = getRawInput(ctx, portName);
        if (raw instanceof ItemStack stack) {
            raw = stack.copy();
        }
        return TypeConverter.convert(raw, type, ctx);
    }

    @Nullable
    protected ExpressionData getInputExpression(GraphDataContext ctx, String portName) {
        Object raw = getRawInput(ctx, portName);
        return raw instanceof DynamicData dynamic ? dynamic.expression() : null;
    }

    protected void putInputExpression(GraphDataContext ctx, String portName, String key,
                                      Map<String, ExpressionData> expressions) {
        ExpressionData expression = getInputExpression(ctx, portName);
        if (expression != null) expressions.put(key, expression);
    }

    protected <T> List<T> getInputList(GraphDataContext ctx, String portName, Class<T> elementType) {
        Object raw = getRawInput(ctx, portName);
        if (raw == null) return List.of();

        // LIST
        if (raw instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<T> castedList = (List<T>) list;
            return castedList;
        }

        // 单体数据
        T converted = TypeConverter.convert(raw, elementType, ctx);
        if (converted != null) {
            return List.of(converted);
        }

        System.err.println("[BaseNode] Error： " + portName + " expect List<" + elementType.getSimpleName() + ">，but received：" + raw.getClass().getSimpleName());
        return List.of();
    }

    protected Map<String, Object> getInputDict(GraphDataContext ctx, String portName) {
        Object raw = getRawInput(ctx, portName);
        if (raw == null) return new java.util.HashMap<>();

        if (raw instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> castedMap = (Map<String, Object>) map;
            return castedMap;
        }

        System.err.println("[BaseNode] Error：" + portName + " expect DICT (Map), but received：" + raw.getClass().getSimpleName());
        return new java.util.HashMap<>();
    }

    /**
     * 将普通数据包装为“可溯源”的动态数据，使客户端能够实时追踪该属性。
     * @param value 当前的物理数值
     * @param target 绑定的目标实体
     * @param propertyName 属性名 (如 "speed", "yaw", "health")
     */
    protected Object bindDynamic(Object value, Entity target, String propertyName) {
        if (target == null) return value;

        ExpressionBinding.Property property = ExpressionBinding.Property.fromId(propertyName);
        if (property == null) return value;
        String varKey = variableKey(target, propertyName);
        return new DynamicData(value, ExpressionData.scalar(varKey,
                Map.of(varKey, entityBinding(target, property))));
    }

    /**
     * 将 Vec3 包装为可被“分离 XYZ”节点解析的动态矢量协议
     */
    protected Object bindDynamicVector(Vec3 value, Entity target, String propertyPrefix) {
        if (target == null) return value;

        String xName = propertyPrefix + "_x";
        String yName = propertyPrefix + "_y";
        String zName = propertyPrefix + "_z";
        ExpressionBinding.Property xProperty = ExpressionBinding.Property.fromId(xName);
        ExpressionBinding.Property yProperty = ExpressionBinding.Property.fromId(yName);
        ExpressionBinding.Property zProperty = ExpressionBinding.Property.fromId(zName);
        if (xProperty == null || yProperty == null || zProperty == null) return value;

        String xKey = variableKey(target, xName);
        String yKey = variableKey(target, yName);
        String zKey = variableKey(target, zName);
        Map<String, ExpressionBinding> bindings = Map.of(
                xKey, entityBinding(target, xProperty),
                yKey, entityBinding(target, yProperty),
                zKey, entityBinding(target, zProperty)
        );
        return new DynamicData(value, ExpressionData.vector(xKey, yKey, zKey, bindings));
    }

    private static ExpressionBinding.EntityProperty entityBinding(Entity entity,
                                                                    ExpressionBinding.Property property) {
        return new ExpressionBinding.EntityProperty(
                entity.level().dimension().identifier().toString(),
                entity.getUUID(),
                entity.getId(),
                property,
                0.0
        );
    }

    private static String variableKey(Entity entity, String property) {
        return "env_" + entity.getUUID().toString().replace("-", "") + "_" + property;
    }

    // ==========================================
    // 流程控制
    // ==========================================

    protected ExecutionResult finish() { return ExecutionResult.finish(); }
    protected ExecutionResult next(String portName) { return ExecutionResult.next(portName); }
}
