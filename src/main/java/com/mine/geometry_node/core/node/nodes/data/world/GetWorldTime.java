package com.mine.geometry_node.core.node.nodes.data.world;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.WorldClock;

import java.util.Optional;

public class GetWorldTime extends BaseNode {

    public static final String TYPE_ID = "get_world_time";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_world_time"))
                .addRow(new PortRow(null, StandardPorts.WORLD_TIME.toOutput(), UIHint.DEFAULT, null, null))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.WORLD_TIME.getId().equals(portName)) {
            return null;
        }

        ServerLevel level = context != null ? context.getLevel() : null;
        if (level == null) {
            return 0L;
        }

        Optional<Holder<WorldClock>> defaultClock = level.dimensionTypeRegistration().value().defaultClock();
        return defaultClock
                .map(clock -> level.getServer().clockManager().getTotalTicks(clock))
                .orElse(0L);
    }
}
