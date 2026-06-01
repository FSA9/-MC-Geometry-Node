package com.mine.geometry_node.client.ui.bottom_window;

import icyllis.modernui.view.View;

/**
 * 所有底部工具窗口必须实现的接口
 */
public interface IToolWindow {

    /**
     * 获取该工具窗口的实际根视图 (用于被添加到 FrameLayout 卡槽中)
     */
    View getView();

    /**
     * 当窗口被选中并显示在屏幕上时调用
     * 可在此处执行：刷新数据、恢复动画、分配内存等懒加载操作
     */
    void onShow();

    /**
     * 当窗口被切换到后台隐藏时调用
     * 可在此处执行：暂停渲染、清理临时缓存等节省性能的操作
     */
    void onHide();
}