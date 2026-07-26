package com.mine.geometry_node.client.ui.UICommand;

/**
 * 命令模式接口
 * 所有对图形数据的修改（如添加节点、移动节点、连线）都必须封装为实现了该接口的对象
 */
public interface ICommand {
    /** 当前图状态下是否允许执行该命令。 */
    default boolean canExecute() {
        return true;
    }

    /** 执行操作 */
    void execute();

    /** 撤销操作 */
    void undo();
}
