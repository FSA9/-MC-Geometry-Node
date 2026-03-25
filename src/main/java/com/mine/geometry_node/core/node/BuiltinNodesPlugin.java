package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.GeometryNodePlugin;

import com.mine.geometry_node.core.node.nodes.actions.player.SendMessage;
import com.mine.geometry_node.core.node.nodes.events.block.OnBlockBreak;


public class BuiltinNodesPlugin implements GeometryNodePlugin {

    @Override
    public void registerNodes(NodeRegistry registry) {
        System.out.println("[GeometryNode] 正在注册内置节点...");

        // Events
        registry.register("events/block", new OnBlockBreak());
        // registry.register("events/block", new OnBlockPlace());
        // registry.register("events/entity", new OnEntityDeath());

        // Actions
        registry.register("actions/player", new SendMessage());
        // registry.register("actions/entity", new AddForce());
        // registry.register("actions/visual", new DrawDebugLine());

        // Maths
        // registry.register("maths/operation", new MathOperation());
    }
}