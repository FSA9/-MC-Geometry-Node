package com.mine.geometry_node.api;

import com.mine.geometry_node.core.node.NodeRegistry;

/**
 * 节点注册插件接口。
 * 无论是内置节点还是第三方 Addon，都通过此接口向系统注册节点。
 */
public interface GeometryNodePlugin {
    /**
     * 插件 ID，用于日志、诊断和后续图兼容信息。
     * 第三方 Addon 建议返回自己的 mod id。
     */
    default String addonId() {
        return getClass().getName();
    }

    /**
     * Registers common marker types. Custom client renderer ids are bound from
     * client-only initialization code so dedicated servers stay client-free.
     */
    default void registerMarkerTypes(MarkerRegistrationContext registry) {
    }

    /**
     * 标准节点注册入口。
     * 新 Addon 优先实现这个方法，避免直接依赖 NodeRegistry 的内部细节。
     */
    default void registerNodes(NodeRegistrationContext registry) {
    }

    /**
     * 旧节点注册入口。
     * 现有内置节点和旧 Addon 可以继续使用；新代码优先使用 NodeRegistrationContext。
     */
    @Deprecated
    default void registerNodes(NodeRegistry registry) {
    }
}
