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
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * [逻辑定义层] 节点行为基类
 */
public abstract class BaseNode {

    private static final ClassValue<Boolean> DYNAMIC_DEFINITION_TYPES = new ClassValue<>() {
        @Override
        protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("getDefinition", NodeData.class).getDeclaringClass() != BaseNode.class;
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("Node definition contract is unavailable for " + type.getName(), e);
            }
        }
    };

    public abstract NodeDef getDefaultDefinition();

    public NodeDef getDefinition(NodeData instanceData) {
        return getDefaultDefinition();
    }

    /** A node is dynamic precisely when it overrides the instance-definition entry point. */
    public final boolean hasDynamicDefinition() {
        return DYNAMIC_DEFINITION_TYPES.get(getClass());
    }

    public final String getTypeId() {
        return getDefaultDefinition().typeId();
    }

    public ExecutionResult execute(ExecutionContext context) {
        return ExecutionResult.finish();
    }

    @Nullable
    public Object compute(GraphDataContext context, String portName) {
        return null;
    }

    // ==========================================
    // 核心数据泵
    // ==========================================

    @Nullable
    private Object resolveInputValue(GraphDataContext ctx, String portName) {
        Object value = resolveInputTransport(ctx, portName);
        return value instanceof DynamicData dynamic ? dynamic.value() : value;
    }

    /**
     * Reads the internal graph transport value, including expression metadata.
     * Ordinary nodes must use one of the typed input methods.
     */
    @Nullable
    private Object resolveInputTransport(GraphDataContext ctx, String portName) {
        return ctx.resolveInput(portName).value();
    }

    @Nullable
    protected <T> T getInput(GraphDataContext ctx, String portName, Class<T> type) {
        return TypeConverter.convert(resolveInputValue(ctx, portName), type, ctx);
    }

    @Nullable
    protected ExpressionData getInputExpression(GraphDataContext ctx, String portName) {
        Object raw = resolveInputTransport(ctx, portName);
        return raw instanceof DynamicData dynamic ? dynamic.expression() : null;
    }

    protected void putInputExpression(GraphDataContext ctx, String portName, String key,
                                      Map<String, ExpressionData> expressions) {
        ExpressionData expression = getInputExpression(ctx, portName);
        if (expression != null) expressions.put(key, expression);
    }

    /**
     * Reads one typed element from a list input without compacting invalid slots.
     * A scalar input is treated as a single-element list at index {@code 0}.
     */
    @Nullable
    protected <T> T getInputFromList(GraphDataContext ctx, String portName, int index,
                                     Class<T> elementType) {
        return TypeConverter.convertFromList(resolveInputValue(ctx, portName), index, elementType, ctx);
    }

    /**
     * Converts every element of a list input. Input isolation is owned by the
     * graph data context; null and rejected slots retain their indexes.
     */
    protected <T> List<T> getInputs(GraphDataContext ctx, String portName, Class<T> elementType) {
        return TypeConverter.convertList(resolveInputValue(ctx, portName), elementType, ctx);
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
