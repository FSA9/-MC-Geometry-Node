package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockStairsShape extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_stairs_shape";

    public SetBlockStairsShape() {
        super(TYPE_ID, "stairs_shape", BlockStateProperties.STAIRS_SHAPE.getName(), "straight", new String[]{"straight", "inner_left", "inner_right", "outer_left", "outer_right"});
    }
}
