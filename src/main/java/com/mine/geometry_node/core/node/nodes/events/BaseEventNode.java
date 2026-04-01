package com.mine.geometry_node.core.node.nodes.events;

import com.mine.geometry_node.core.execution.ExecutionContext;
import com.mine.geometry_node.core.execution.ExecutionResult;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.port.StandardPorts;
import org.jetbrains.annotations.Nullable;

/**
 * [事件节点基类]
 * 专门为系统触发的事件节点提供默认实现。
 * 自动处理 Flow 的发射和 EventData 的透传，极大减少子类的样板代码。
 */
public abstract class BaseEventNode extends BaseNode {

    /**
     * [统一的执行流逻辑]
     * 事件节点被引擎触发后，默认将执行流传递给名为 "flow" 的标准输出端口。
     */
    @Override
    public ExecutionResult execute(ExecutionContext context) {
        return next(StandardPorts.FLOW_OUT.getId());
    }

    /**
     * [统一的数据流逻辑]
     * 由于我们强制规定了“端口 ID”与底层引擎注入的“EventData Key”保持一致，
     * 所以这里只需直接通过请求的 portName 从上下文中取出对应数据即可。
     */
    @Override
    @Nullable
    public Object compute(ExecutionContext context, String portName) {
        return context.getEventData(portName);
    }
}