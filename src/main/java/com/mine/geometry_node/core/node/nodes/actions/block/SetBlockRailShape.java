package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockRailShape extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_rail_shape";

    public SetBlockRailShape() {
        super(TYPE_ID, BlockStateProperties.RAIL_SHAPE.getName(), BlockStateProperties.RAIL_SHAPE, "north_south");
    }
}
