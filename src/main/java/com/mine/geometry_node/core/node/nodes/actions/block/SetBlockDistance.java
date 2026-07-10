package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockDistance extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_distance";

    public SetBlockDistance() {
        super(TYPE_ID, BlockStateProperties.DISTANCE.getName(), BlockStateProperties.DISTANCE, 1, 1, 7);
    }
}
