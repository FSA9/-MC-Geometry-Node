package com.mine.geometry_node.core.node.nodes.data.type;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import com.mine.geometry_node.core.node.RegistryDataManager;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.definition.node.NodeDef;
import com.mine.geometry_node.core.node.definition.node.NodeType;
import com.mine.geometry_node.core.node.definition.port.PortRow;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.definition.port.UIHint;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class GetBlockState extends BaseNode {

    public static final String TYPE_ID = "get_block_state";

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.DATA, Component.translatable("geometry_node.node.get_block_state"))
                .addRow(new PortRow(null, StandardPorts.BLOCK_STATE.toOutput(), UIHint.DEFAULT, null, null))
                .addPassthroughInput(StandardPorts.STRING.toInput("minecraft:stone").hiddenPin(), UIHint.SELECT, null, Map.of(PortMetaKeys.OPTIONS, RegistryDataManager.getAllBlocks().toArray(new String[0])))
                .build();
    }

    @Override
    public Object compute(GraphDataContext context, String portName) {
        if (!StandardPorts.BLOCK_STATE.getId().equals(portName)) {
            return null;
        }

        BlockState state = getInput(context, StandardPorts.STRING.getId(), BlockState.class);
        return state != null ? state : Blocks.AIR.defaultBlockState();
    }
}
