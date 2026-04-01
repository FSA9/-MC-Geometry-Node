package com.mine.geometry_node.core.node.nodes;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.TypeConverter;
import org.jetbrains.annotations.Nullable;

import java.util.List;

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
        Object val = ctx.getInputValue(portName);
        if (val == null) {
            val = ctx.getStaticInput(portName);
        }
        return val;
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

    /**
     * [严格列表获取]
     * 不再进行自动装箱！只接收真正的 List 类型数据。
     * 如果传入的不是 List，直接返回空列表（避免制造内存垃圾）。
     */
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

        System.err.println("[BaseNode] 警告：端口 " + portName + " 期望 List<" + elementType.getSimpleName() + ">，但收到了无法转换的单体数据：" + raw.getClass().getSimpleName());
        return List.of();
    }

    // ==========================================
    // 流程控制
    // ==========================================

    protected ExecutionResult finish() { return ExecutionResult.finish(); }
    protected ExecutionResult next(String portName) { return ExecutionResult.next(portName); }
}