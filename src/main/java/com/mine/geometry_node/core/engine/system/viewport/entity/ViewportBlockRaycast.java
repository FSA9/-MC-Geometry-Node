package com.mine.geometry_node.core.engine.system.viewport.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Ray queries never create/load chunks, including long orthographic near-plane rays. */
public final class ViewportBlockRaycast {
    private ViewportBlockRaycast() {}
    public static BlockHitResult clip(Level level, Vec3 from, Vec3 to) {
        BlockGetter loaded = new BlockGetter() {
            @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
            @Override public BlockState getBlockState(BlockPos p) {
                var chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                return chunk != null ? chunk.getBlockState(p) : Blocks.AIR.defaultBlockState();
            }
            @Override public FluidState getFluidState(BlockPos p) {
                var chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
                return chunk != null ? chunk.getFluidState(p) : Fluids.EMPTY.defaultFluidState();
            }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinY() { return level.getMinY(); }
        };
        return loaded.clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,CollisionContext.empty()));
    }
}
