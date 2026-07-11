package com.mine.geometry_node.core.node.nodes.actions.block;

import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionContext;
import com.mine.geometry_node.core.engine.blueprint.runtime.ExecutionResult;
import com.mine.geometry_node.core.node.meta.PortMetaKeys;
import com.mine.geometry_node.core.node.nodes.BaseNode;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.nodes.NodeType;
import com.mine.geometry_node.core.node.port.PortRow;
import com.mine.geometry_node.core.node.port.StandardPorts;
import com.mine.geometry_node.core.node.port.UIHint;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Map;

public class SetBlocksOnGeometry extends BaseNode {
    public static final String TYPE_ID = "set_blocks_on_geometry";
    private static final String[] VOXEL_MODE_OPTIONS = {
            GeometryValue.VoxelMode.SURFACE.id(),
            GeometryValue.VoxelMode.VOLUME.id()
    };

    private static final int DEFAULT_MAX_BLOCKS = 32768;
    private static final int HARD_MAX_BLOCKS = 262144;
    private static final int DIRECT_SET_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    @Override
    public NodeDef getDefaultDefinition() {
        return NodeDef.builder(TYPE_ID, NodeType.ACTION, Component.translatable("geometry_node.node.set_blocks_on_geometry"))
                .addRow(new PortRow(StandardPorts.FLOW_IN.toExec(), StandardPorts.FLOW_OUT.toExec(), UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.GEOMETRY.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.BLOCK_STATE.toInput(), null, UIHint.DEFAULT, null, null))
                .addRow(new PortRow(StandardPorts.TRANSLATION.toInput(Vec3.ZERO), null, UIHint.VECTOR, null, null))
                .addRow(new PortRow(
                        StandardPorts.VOXEL_MODE.toInput(GeometryValue.VoxelMode.SURFACE.id()).hiddenPin(),
                        null,
                        UIHint.SELECT,
                        null,
                        Map.of(PortMetaKeys.OPTIONS, VOXEL_MODE_OPTIONS)
                ))
                .addRow(new PortRow(StandardPorts.MAX_BLOCKS.toInput(DEFAULT_MAX_BLOCKS), null, UIHint.INPUT, null, null))
                .addRow(new PortRow(StandardPorts.REPLACE_EXISTING.toInput(true), null, UIHint.CHECKBOX, null, null))
                .build();
    }

    @Override
    public ExecutionResult execute(ExecutionContext context) {
        GeometryValue geometry = getInput(context, StandardPorts.GEOMETRY.getId(), GeometryValue.class);
        BlockState state = getInput(context, StandardPorts.BLOCK_STATE.getId(), BlockState.class);

        if (geometry == null || geometry.isEmpty() || state == null || !(context.getLevel() instanceof ServerLevel level)) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        GeometryValue.VoxelMode mode = GeometryValue.VoxelMode.fromId(getInput(context, StandardPorts.VOXEL_MODE.getId(), String.class));
        Vec3 translation = getInput(context, StandardPorts.TRANSLATION.getId(), Vec3.class);
        Integer rawMaxBlocks = getInput(context, StandardPorts.MAX_BLOCKS.getId(), Integer.class);
        Boolean replaceExisting = getInput(context, StandardPorts.REPLACE_EXISTING.getId(), Boolean.class);
        int maxBlocks = clampMaxBlocks(rawMaxBlocks);

        if (maxBlocks <= 0) {
            return next(StandardPorts.FLOW_OUT.getId());
        }

        long estimate = geometry.estimateBlockCount(mode, translation);
        if (estimate > maxBlocks) {
            System.err.println("[GeometryNode] SetBlocksOnGeometry blocked: estimated block count " + estimate + " exceeds max_blocks=" + maxBlocks + ".");
            return next(StandardPorts.FLOW_OUT.getId());
        }

        LongOpenHashSet positions = new LongOpenHashSet(Math.max(16, Math.min(maxBlocks, (int) Math.min(Integer.MAX_VALUE, estimate))));
        boolean[] truncated = {false};
        boolean completed = geometry.forEachBlockPosition(mode, translation, packed -> {
            if (positions.size() >= maxBlocks && !positions.contains(packed)) {
                truncated[0] = true;
                return false;
            }
            positions.add(packed);
            return true;
        });

        if (!completed || truncated[0]) {
            System.err.println("[GeometryNode] SetBlocksOnGeometry blocked: geometry expansion exceeded max_blocks=" + maxBlocks + ".");
            return next(StandardPorts.FLOW_OUT.getId());
        }

        writeBlocks(level, state, positions, replaceExisting == null || replaceExisting);
        return next(StandardPorts.FLOW_OUT.getId());
    }

    private static void writeBlocks(ServerLevel level, BlockState state, LongOpenHashSet positions, boolean replaceExisting) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        LongOpenHashSet loadedChunks = new LongOpenHashSet();
        LongOpenHashSet unloadedChunks = new LongOpenHashSet();

        for (LongIterator iterator = positions.iterator(); iterator.hasNext(); ) {
            long packed = iterator.nextLong();
            pos.set(packed);

            long chunkKey = ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
            if (unloadedChunks.contains(chunkKey)) {
                continue;
            }
            if (!loadedChunks.contains(chunkKey)) {
                if (!level.isLoaded(pos)) {
                    unloadedChunks.add(chunkKey);
                    continue;
                }
                loadedChunks.add(chunkKey);
            }

            if (!replaceExisting && !level.isEmptyBlock(pos)) {
                continue;
            }
            level.setBlock(pos, state, DIRECT_SET_FLAGS);
        }
    }

    private static int clampMaxBlocks(Integer maxBlocks) {
        if (maxBlocks == null) {
            return DEFAULT_MAX_BLOCKS;
        }
        return Math.max(0, Math.min(HARD_MAX_BLOCKS, maxBlocks));
    }
}
