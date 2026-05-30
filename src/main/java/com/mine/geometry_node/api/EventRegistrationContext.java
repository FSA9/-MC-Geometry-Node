package com.mine.geometry_node.api;

/**
 * 事件注册上下文。
 * 第一阶段只标准化事件定义与事件派发，不处理取消/修改原事件结果。
 */
public interface EventRegistrationContext {
    String addonId();

    GeometryEventDispatcher dispatcher();

    void registerEvent(EventDef eventDef);
}
