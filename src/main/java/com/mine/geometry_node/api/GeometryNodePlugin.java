package com.mine.geometry_node.api;

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

    /** 节点注册入口。Addon 只通过公开注册上下文声明节点。 */
    default void registerNodes(NodeRegistrationContext registry) {
    }
}
