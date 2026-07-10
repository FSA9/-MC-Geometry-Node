package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockLevel extends AbstractSetBlockIntegerProperty {
    public static final String TYPE_ID = "set_block_level";

    public SetBlockLevel() {
        super(TYPE_ID, BlockStateProperties.LEVEL.getName(), BlockStateProperties.LEVEL, 0, 0, 15);
    }
}
