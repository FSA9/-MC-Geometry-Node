package com.mine.geometry_node.api;

/**
 * 事件作用域。
 * 第一阶段仅用于声明和校验，不改变现有执行模型。
 */
public enum EventScope {
    GLOBAL,
    LEVEL,
    ENTITY
}
