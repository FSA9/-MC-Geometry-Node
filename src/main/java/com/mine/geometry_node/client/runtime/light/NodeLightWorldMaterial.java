package com.mine.geometry_node.client.runtime.light;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/** R8 transport codes below 128 preserve the model lighting's existing >0.5 blocker test. */
public final class NodeLightWorldMaterial {
    public static final int AIR = 0, CLEAR_GLASS = 17, PANE = 32, WATER = 64, OPAQUE = 255;

    private NodeLightWorldMaterial() {}

    public static int encode(BlockState state, BlockGetter world, BlockPos pos) {
        int emission = Math.clamp(state.getLightEmission(world, pos), 0, 15);
        if (state.getLightDampening() >= 15) return emission | 16;
        int material = AIR;
        if (state.getBlock() instanceof StainedGlassBlock glass) material = glass.getColor().getId() + 1;
        else if (state.getBlock() instanceof StainedGlassPaneBlock glass) material = PANE | (glass.getColor().getId() + 1);
        else if (state.is(Blocks.GLASS)) material = CLEAR_GLASS;
        else if (state.is(Blocks.GLASS_PANE)) material = PANE | CLEAR_GLASS;
        if (state.getFluidState().is(FluidTags.WATER)) material |= WATER;
        return emission | (material << 8);
    }

    /** Low bits retain emission/blocker metadata; high bits carry the optional optical material. */
    public static byte cell(int encoded) {
        return (byte) (encoded < 0 || (encoded & 16) != 0 ? OPAQUE : (encoded >>> 8) & 127);
    }
}
