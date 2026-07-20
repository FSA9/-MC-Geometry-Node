package com.mine.geometry_node.core.schematic;

import java.util.List;

public record SchematicData(
        int width,
        int height,
        int length,
        List<Block> blocks,
        List<BlockEntity> blockEntities,
        List<Entity> entities,
        boolean truncated
) {
    public SchematicData {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        blockEntities = blockEntities == null ? List.of() : List.copyOf(blockEntities);
        entities = entities == null ? List.of() : List.copyOf(entities);
    }

    public boolean isEmpty() {
        return blocks.isEmpty() && blockEntities.isEmpty() && entities.isEmpty();
    }

    public record Block(int x, int y, int z, String state, int color) {
        public Block {
            state = state == null ? "" : state;
        }
    }

    public record BlockEntity(int x, int y, int z, net.minecraft.nbt.CompoundTag tag) {
        public BlockEntity {
            tag = tag == null ? new net.minecraft.nbt.CompoundTag() : tag.copy();
        }
    }

    public record Entity(double x, double y, double z, net.minecraft.nbt.CompoundTag tag) {
        public Entity {
            tag = tag == null ? new net.minecraft.nbt.CompoundTag() : tag.copy();
        }
    }
}
