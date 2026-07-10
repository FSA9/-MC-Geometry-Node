package com.mine.geometry_node.core.node.nodes.actions.block;

import net.minecraft.world.level.block.state.properties.BlockStateProperties;

public class SetBlockSlabType extends AbstractSetBlockEnumProperty {
    public static final String TYPE_ID = "set_block_slab_type";

    public SetBlockSlabType() {
        super(TYPE_ID, "slab_type", BlockStateProperties.SLAB_TYPE.getName(), "bottom", new String[]{"top", "bottom", "double"});
    }
}
