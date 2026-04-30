package com.mine.geometry_node.core.node.nodes;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.execution.datatypes.DynamicData;
import com.mine.geometry_node.core.execution.datatypes.ExpressionData;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.TypeConverter;
import net.minecraft.world.entity.Entity;
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
    public Object compute(ExecutionContext context, String portName) {
        return null;
    }

    // ==========================================
    // 核心数据泵
    // ==========================================

    /**
     * 仅负责找数据（连线 > 静态配置）
     */
    @Nullable
    protected Object getRawInput(ExecutionContext ctx, String portName) {
        // 1. 尝试获取连线输入
        Object val = ctx.getInputValue(portName);
        if (val != null) return val;

        // 2. 尝试获取实例化的静态配置 (JSON 存储的值)
        val = ctx.getStaticInput(portName);
        if (val != null) return val;

        // 3. 如果前两者都为空，则从节点定义的默认值中提取
        NodeDef def = getDefaultDefinition();
        if (def != null) {
            for (var row : def.rows()) {
                // 检查左侧输入端口
                if (row.leftPort() != null && row.leftPort().id().equals(portName)) {
                    return row.leftPort().defaultValue();
                }
                // 检查右侧输出端口 (虽然通常不会在输入时查输出，但为了严谨性)
                if (row.rightPort() != null && row.rightPort().id().equals(portName)) {
                    return row.rightPort().defaultValue();
                }
            }
        }

        return null;
    }

    /**
     * [全能单体获取]
     * 获取数据，并委派给 TypeConverter 变身为你想要的 Class。
     */
    @Nullable
    protected <T> T getInput(ExecutionContext ctx, String portName, Class<T> type) {
        Object raw = getRawInput(ctx, portName);
        return TypeConverter.convert(raw, type, ctx);
    }

    protected <T> List<T> getInputList(ExecutionContext ctx, String portName, Class<T> elementType) {
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

    protected Map<String, Object> getInputDict(ExecutionContext ctx, String portName) {
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

        // 生成唯一的变量标识符，防止多个实体混淆
        String varKey = "env_" + target.getId() + "_" + propertyName;
        // 定义绑定协议：客户端看到这个 key，就知道去查对应实体的属性
        Map<String, String> bindings = Map.of(varKey, "entity:" + target.getId() + ":" + propertyName);

        return new DynamicData(value, new ExpressionData(varKey, bindings));
    }

    /**
     * 将 Vec3 包装为可被“分离 XYZ”节点解析的动态矢量协议
     */
    protected Object bindDynamicVector(Vec3 value, Entity target, String propertyPrefix) {
        if (target == null) return value;

        // 生成三个轴的变量名
        String xKey = "env_" + target.getId() + "_" + propertyPrefix + "_x";
        String yKey = "env_" + target.getId() + "_" + propertyPrefix + "_y";
        String zKey = "env_" + target.getId() + "_" + propertyPrefix + "_z";

        // 生成三个轴的独立抓取协议
        Map<String, String> bindings = Map.of(
                xKey, "entity:" + target.getId() + ":" + propertyPrefix + "_x",
                yKey, "entity:" + target.getId() + ":" + propertyPrefix + "_y",
                zKey, "entity:" + target.getId() + ":" + propertyPrefix + "_z"
        );

        // 组装特有的矢量公式协议，例如 "vec3(env_123_vel_x, env_123_vel_y, env_123_vel_z)"
        String vectorFormula = "vec3(" + xKey + "," + yKey + "," + zKey + ")";

        return new DynamicData(value, new ExpressionData(vectorFormula, bindings));
    }

    // ==========================================
    // 流程控制
    // ==========================================

    protected ExecutionResult finish() { return ExecutionResult.finish(); }
    protected ExecutionResult next(String portName) { return ExecutionResult.next(portName); }
}