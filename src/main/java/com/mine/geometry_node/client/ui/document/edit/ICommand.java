package com.mine.geometry_node.client.ui.document.edit;

/**
 * 命令模式接口
 * 所有对图形数据的修改（如添加节点、移动节点、连线）都必须封装为实现了该接口的对象。
 * 必须通过 CommandManager 提交；管理器以完整文档快照记录 Undo/Redo，包含动态端口清理等副作用。
 * 命令本身只执行首次修改，不会在 Redo 时被再次执行，不能依赖命令持有的节点对象作为历史状态。
 */
public interface ICommand {
    /** 当前图状态下是否允许执行该命令。 */
    default boolean canExecute() {
        return true;
    }

    /** Translation key for the history action label. */
    default String historyLabel() { return "geometry_node.history.graph.edit"; }

    /** 执行操作 */
    void execute();

}
