package com.mine.geometry_node.api;

import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.NodeCapabilities;

/**
 * 节点注册上下文。
 * Addon 通过此接口注册节点，而不是直接依赖 NodeRegistry 的内部结构。
 */
public interface NodeRegistrationContext {
    String addonId();

    void registerNode(String menuPath, BaseNode node);

    default void registerNode(String menuPath, BaseNode node, NodeCapabilities capabilities) {
        registerNode(menuPath, node);
    }

    default void register(String menuPath, BaseNode node) {
        registerNode(menuPath, node);
    }

    default void register(String menuPath, BaseNode node, NodeCapabilities capabilities) {
        registerNode(menuPath, node, capabilities);
    }
}
