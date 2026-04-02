package com.mine.geometry_node.core.node;

import com.mine.geometry_node.api.GeometryNodePlugin;

import com.mine.geometry_node.core.node.nodes.actions.entity.AddForce;
import com.mine.geometry_node.core.node.nodes.actions.player.SendMessage;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawDebugLine;
import com.mine.geometry_node.core.node.nodes.data.type.GetDamageType;
import com.mine.geometry_node.core.node.nodes.data.type.GetEffect;
import com.mine.geometry_node.core.node.nodes.events.block.OnBlockBreak;
import com.mine.geometry_node.core.node.nodes.events.block.OnBlockPlace;
import com.mine.geometry_node.core.node.nodes.events.entity.OnEntityDeath;
import com.mine.geometry_node.core.node.nodes.logics.Switch;
import com.mine.geometry_node.core.node.nodes.maths.operation.MathExpression;
import com.mine.geometry_node.core.node.nodes.maths.operation.MathOperation;


public class BuiltinNodesPlugin implements GeometryNodePlugin {

    @Override
    public void registerNodes(NodeRegistry registry) {
        System.out.println("[GeometryNode] 正在注册内置节点...");

        // Events
        registry.register("events/block", new OnBlockBreak());
        registry.register("events/block", new OnBlockPlace());
        registry.register("events/entity", new OnEntityDeath());

        // Actions
        registry.register("actions/player", new SendMessage());
        registry.register("actions/entity", new AddForce());
        registry.register("actions/visual", new DrawDebugLine());

        // Data
//        registry.register("data/type", new GetEffect());
//        registry.register("data/type", new GetDamageType());


        // Maths
        registry.register("maths/operation", new MathExpression());
        registry.register("maths/operation", new MathOperation());

        // Logics
        registry.register("logics", new Switch());
    }
}