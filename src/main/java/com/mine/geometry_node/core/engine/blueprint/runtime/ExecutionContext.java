package com.mine.geometry_node.core.engine.blueprint.runtime;

import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateTarget;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.engine.graph.value.GraphValueSnapshot;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * [执行上下文接口]
 * <p>
 * 定义了节点在执行期间可以访问的“环境能力”。
 * 这是一个 {@code Facade} (外观模式) 接口，用于向节点隐藏 {@link BlueprintProcess} 的底层复杂性（如指针操作、序列化逻辑）。
 * 节点通过此接口读取图数据，并使用明确的运行时能力与世界交互。
 */
public interface ExecutionContext extends GraphDataContext {

    /**
     * 获取当前图所属的世界。
     */
    ServerLevel getLevel();

    /**
     * Returns the entity in the current execution/event context. This may be
     * an event subject and is therefore not necessarily the graph owner.
     */
    @Nullable
    Entity getEntity();

    /**
     * Returns the entity that owns the current graph binding. Unlike
     * {@link #getEntity()}, this is not replaced by an event subject and is
     * always {@code null} for level/global graph processes.
     */
    @Nullable
    Entity getGraphOwnerEntity();

    /**
     * 获取当前节点被激活时，对应的执行输入端口名。
     * <p>
     * 专为具有多个执行输入口（如 Gate, Sequence, Merge）的节点设计。
     * 普通单输入节点无需调用此方法。
     *
     * @return 触发当前节点执行的输入端口名 (例如 "flow_in_A")
     */
    String getEntryPort();

    /**
     * 获取当前运行的图 ID（用于调试或跨图调用）。
     */
    String getGraphId();

    /**
     * [数据拉取] 获取连接到指定输入端口的上游数据。
     * <p>
     * 该方法会根据 {@link BlueprintPlan} 自动查找是谁连到了当前节点的 portName 端口，
     * 并递归触发上游节点的求值逻辑。
     *
     * @param portName 当前节点的输入端口名
     * @return 上游节点返回的数据，若未连接或执行异常则返回 null
     */
    @Nullable
    Object getInputValue(String portName);

    /**
     * [新增] 获取节点端口的静态默认值。
     * 对应 JSON 中的 "inputs" 字段。通常用于当端口未连线时的回落值。
     */
    @Nullable
    Object getStaticInput(String portName);

    /**
     * [事件参数读取] 获取系统注入的底层物理事件参数（如方块坐标、伤害来源）。
     * 与普通的局部变量隔离，防止被同名变量覆盖。
     * @param key 参数名称 (例如 "evt_pos")
     * @return 参数值
     */
    @Nullable
    Object getEventData(String key);

    /**
     * Returns the runtime node ID of the event node that started this execution flow.
     * A negative value means that the flow was not started by an event.
     */
    int getEventSourceNodeId();

    /**
     * [事件参数写入] 仅供引擎在分发事件时调用，向运行时注入瞬时环境数据。
     */
    void setEventData(String key, Object value);

    /**
     * 检查当前执行节点是否定义了某个特定的输入端口。
     * 用于 Switch 等动态端口节点进行循环探测。
     */
    boolean hasPort(String portName);

    /**
     * [持久化属性写入] 设置实体、全局或命名作用域的持久化属性。
     * @param target 持久属性目标
     * @param name 属性名
     * @param value 属性值；Java null 非法，删除必须调用 clearScopedState
     */
    void setScopedState(ScopedStateTarget target, String name, Object value);

    /**
     * [持久化属性读取]
     */
    @Nullable
    Object getScopedState(ScopedStateTarget target, String name);

    boolean hasScopedState(ScopedStateTarget target, String name);

    boolean clearScopedState(ScopedStateTarget target, String name);

    // ==========================================
    // 高级控制流与引擎特权 API
    // ==========================================

    /**
     * [缓存控制] 清空当前帧的数据运算缓存。
     * 通常由循环节点（如 ForEachLoop）在每次迭代开始时调用，强制下游运算节点重新求值，
     * 避免同一 Tick 内循环读取到陈旧的缓存数据。
     */
    void clearFrameCache();

    /**
     * Starts one child branch and resumes the current node only after that branch has
     * fully completed, including any tick or external waits inside it.
     *
     * @param branchPortName current node output used to start the child branch
     * @param resumeEntryPort entry name used when the current node is resumed
     * @return whether a connected child branch was started
     */
    boolean executeBranchThenResume(String branchPortName, String resumeEntryPort);

    /**
     * [子分支汇合] 创建一个 join group。调用方可以启动多个子分支，
     * 并在所有子分支完成后再触发指定的完成端口。
     *
     * @param completedPortName 当前节点上的完成输出端口
     * @return join group id
     */
    String createBranchJoin(String completedPortName);

    /**
     * [子分支执行] 启动当前节点指定输出端口上的独立子执行流。
     * 子分支拥有独立的事件寄存器与临时黑板副本，可以安全跨 tick 等待。
     *
     * @param portName 当前节点的输出执行端口名
     * @param tempDataOverride 子分支临时黑板覆盖值
     * @param joinId 可选 join group id；非空时子分支结束会参与汇合计数
     * @return 是否成功启动了子分支
     */
    boolean spawnBranch(String portName, @Nullable Map<String, Object> tempDataOverride, @Nullable String joinId);

    /**
     * [子分支汇合] 标记某个 join group 已经完成所有子分支的发起。
     * 如果此时没有待完成子分支，则立即触发完成端口。
     */
    void finishBranchJoin(String joinId);

    /**
     * [临时黑板写入] 设置当前图进程级别的瞬时态数据。
     * 专为控制流节点保存内部游标 (如 Index, Current Element) 设计，防止污染常规局部变量。
     *
     * @param key 数据的唯一键
     * @param value 数据值
     */
    void setTempData(String key, Object value);

    /**
     * [临时黑板读取] 获取瞬时态数据。
     */
    @Nullable
    Object getTempData(String key);

    /**
     * [临时黑板清理] 移除指定的瞬时态数据，防止内存泄漏。
     */
    void removeTempData(String key);

    /**
     * Stores an execution result owned by the current node instance.
     * Results with the same port ID on different nodes are isolated.
     */
    default void setNodeResult(String portName, @Nullable Object value) {
        setTempData(nodeResultKey(getCurrentNodeId(), portName), value);
        clearFrameCache();
    }

    /** Reads a result previously stored by the current node instance. */
    @Override
    @Nullable
    default Object getNodeResult(String portName) {
        Object value = getTempData(nodeResultKey(getCurrentNodeId(), portName));
        return GraphValueSnapshot.requiresReadCopy(value)
                ? GraphValueSnapshot.snapshot(value) : value;
    }

    @Override
    default boolean isCurrentEventSourceNode() {
        return getCurrentNodeId() == getEventSourceNodeId();
    }

    static String nodeResultKey(int nodeId, String portName) {
        return "node_result:" + nodeId + ":" + portName;
    }

    /**
     * 获取当前正在执行（或计算）的节点运行时 ID。
     */
    int getCurrentNodeId();

    /**
     * 获取当前节点在图 JSON 中的稳定字符串 ID。
     */
    @Nullable
    default String getCurrentNodeStableId() {
        int nodeId = getCurrentNodeId();
        return nodeId >= 0 ? String.valueOf(nodeId) : null;
    }

    /**
     * [异步调度] 将指定的节点加入延迟唤醒队列，并显式指定唤醒时的执行输入端口。
     * <p>
     * 完美支持多执行输入节点（如 Merge 节点）。当延迟结束时，虚拟机将脉冲精准注入指定的入口。
     *
     * @param nodeId 目标节点的运行时 ID
     * @param delayTicks 延迟的刻数 (Ticks)
     * @param entryPortName 唤醒时进入的执行输入端口名 (例如 "flow_in_A")
     */
    void scheduleNode(int nodeId, long delayTicks, String entryPortName);

    /**
     * [重构后] 视觉特效广播
     * @param extraData 动态物理数据夹 (包含起点、终点等任意定制化数据)
     */
    void broadcastDynamicVisual(String effectType, int color, int durationTicks,
                                Map<String, ExpressionData> expressions,
                                net.minecraft.nbt.CompoundTag extraData);

    void broadcastDynamicVisual(String effectType, int color, int durationTicks,
                                Map<String, ExpressionData> expressions,
                                net.minecraft.nbt.CompoundTag extraData,
                                net.minecraft.world.phys.Vec3 center,
                                double radius,
                                java.util.List<com.mine.geometry_node.core.engine.service.GraphEngineServices.VisualAsset> assets);
}
