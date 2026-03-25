package com.mine.geometry_node.api;

import com.mine.geometry_node.core.node.NodeRegistry;

/**
 * 节点注册插件接口。
 * 无论是内置节点还是第三方 Addon，都通过此接口向系统注册节点。
 */
public interface GeometryNodePlugin {
    void registerNodes(NodeRegistry registry);
}