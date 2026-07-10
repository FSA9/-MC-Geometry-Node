package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockInWall extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_in_wall";

    public SetBlockInWall() {
        super(TYPE_ID, BlockStateProperties.IN_WALL.getName(), BlockStateProperties.IN_WALL, false);
    }
}
