package com.mine.geometry_node.core.node.port;

/**
 * [UI 暗示]
 * 数据层对 UI 渲染层的纯粹暗示
 */
public enum UIHint {
    DEFAULT,        // 默认
    SLIDER,         // 滑动条
    SELECT,         // 下拉框
    CHECKBOX,       // 勾选框
    INPUT,          // 输入框
    CUSTOM          // 自定义复杂组件
}