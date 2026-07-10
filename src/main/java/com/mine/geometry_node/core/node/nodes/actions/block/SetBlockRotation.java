package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockRotation extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_rotation";

    public SetBlockRotation() {
        super(TYPE_ID, BlockStateProperties.ROTATION_16.getName(), BlockStateProperties.ROTATION_16, 0, 0, 15);
    }
}
