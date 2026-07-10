package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockCharges extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_charges";

    public SetBlockCharges() {
        super(TYPE_ID, BlockStateProperties.RESPAWN_ANCHOR_CHARGES.getName(), BlockStateProperties.RESPAWN_ANCHOR_CHARGES, 0, 0, 4);
    }
}
