package com.mine.geometry_node.core.execution;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;

/**
 * [执行上下文接口]
 * <p>
 * 定义了节点在执行期间可以访问的“环境能力”。
 * 这是一个 {@code Facade} (外观模式) 接口，用于向节点隐藏 {@link GraphProcess} 的底层复杂性（如指针操作、序列化逻辑）。
 * 节点只能通过此接口与世界交互或读写变量。
 */
public interface ExecutionContext {

    /**
     * 获取当前图所属的世界。
     */
    ServerLevel getLevel();

    /**
     * 获取绑定该图的实体（如果有）。
     * @return 实体对象，如果图是依附于非实体对象（如全局事件）运行，可能为 null。
     */
    @Nullable
    Entity getEntity();

    /**
     * [变量读取] 获取局部变量。
     * <p>
     * 这里的变量系统应设计为强类型安全，建议后续配合 VarType 使用。
     * @param name 变量名
     * @return 变量值，若不存在则返回 null
     */
    @Nullable
    Object getVariable(String name);

    /**
     * [变量写入] 设置局部变量。
     * <p>
     * 实现类需在此处进行类型白名单检查 (Int/Float/String/UUID/BlockPos)，
     * 拒绝不支持序列化的复杂对象。
     * @param name 变量名
     * @param value 变量值
     */
    void setVariable(String name, Object value);

    /**
     * 获取当前运行的图 ID（用于调试或跨图调用）。
     */
    String getGraphId();

    /**
     * [数据拉取] 获取连接到指定输入端口的上游数据。
     * <p>
     * 该方法会根据 {@link RuntimeGraphIndex} 自动查找是谁连到了当前节点的 portName 端口，
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
     * [事件参数写入] 仅供引擎在分发事件时调用，向运行时注入瞬时环境数据。
     */
    void setEventData(String key, Object value);

    /**
     * 检查当前执行节点是否定义了某个特定的输入端口。
     * 用于 Switch 等动态端口节点进行循环探测。
     */
    boolean hasPort(String portName);

    /**
     * [持久化属性写入] 设置实体或全局的持久化属性。
     * @param target 目标实体。若为 null，则设置到当前世界的全局存储中。
     * @param name 属性名
     * @param value 属性值 (传 null 视为删除)
     */
    void setPersistentAttribute(@Nullable Object target, String name, Object value);

    /**
     * [持久化属性读取]
     */
    @Nullable
    Object getPersistentAttribute(@Nullable Object target, String name);

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
     * [同步分支执行] 挂起当前节点的执行流，立即将指定的输出分支压入子栈并同步跑完。
     * 只有当该分支（及其所有的后续连线）彻底触底结束后，此方法才会返回 (阻塞式调用)。
     * 专用于 ForEachLoop 等需要往复执行的循环节点。
     *
     * @param portName 当前节点的输出执行端口名 (如 "loop")
     */
    void executeBranchSync(String portName);

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
     * 获取当前正在执行（或计算）的节点运行时 ID。
     */
    int getCurrentNodeId();

    /**
     * [异步调度] 将指定的节点加入延迟唤醒队列。
     * @param nodeId 节点 ID
     * @param delayTicks 延迟的刻数
     */
    void scheduleNode(int nodeId, long delayTicks);

    /**
     * [视觉特效广播]
     * 向世界中指定坐标附近的客户端下发纯视觉渲染指令。
     * * @param effectType 特效类型标识 (如 "debug_line")
     * @param sourceEntityId 起点绑定的实体ID (-1 表示不绑定，使用死坐标)
     * @param startPos 起点绝对坐标 (或锚点的局部偏移量)
     * @param targetEntityId 终点绑定的实体ID (-1 表示不绑定)
     * @param endPos 终点绝对坐标 (或锚点的局部偏移量)
     * @param color 颜色 (ARGB)
     * @param size 尺寸/粗细
     * @param durationTicks 持续时间
     */
    void broadcastVisual(String effectType, int sourceEntityId, net.minecraft.world.phys.Vec3 startPos,
                         int targetEntityId, net.minecraft.world.phys.Vec3 endPos,
                         int color, float size, int durationTicks);


    /**
     * [重构后] 视觉特效广播
     * @param extraData 动态物理数据夹 (包含起点、终点等任意定制化数据)
     */
    void broadcastDynamicVisual(String effectType, int color, int durationTicks,
                                Map<String, String> expressions,
                                Map<String, String> bindings,
                                net.minecraft.nbt.CompoundTag extraData);
}