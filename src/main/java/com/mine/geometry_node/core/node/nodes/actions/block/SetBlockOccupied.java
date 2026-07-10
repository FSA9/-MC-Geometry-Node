package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockOccupied extends AbstractSetBlockBooleanProperty {
    public static final String TYPE_ID = "set_block_occupied";

    public SetBlockOccupied() {
        super(TYPE_ID, BlockStateProperties.OCCUPIED.getName(), BlockStateProperties.OCCUPIED, false);
    }
}
