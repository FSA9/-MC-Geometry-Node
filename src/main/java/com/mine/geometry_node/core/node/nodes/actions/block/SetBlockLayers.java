package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockLayers extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_layers";

    public SetBlockLayers() {
        super(TYPE_ID, BlockStateProperties.LAYERS.getName(), BlockStateProperties.LAYERS, 1, 1, 8);
    }
}
