package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketSchematicProjection(
        String key,
        String graphId,
        String dimension,
        double originX,
        double originY,
        double originZ,
        int width,
        int height,
        int length,
        int durationTicks,
        float alpha,
        boolean truncated,
        List<String> states,
        List<Block> blocks,
        List<BlockEntity> blockEntities,
        List<Entity> entities
) implements CustomPacketPayload {
    public static final Type<PacketSchematicProjection> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "schematic_projection"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketSchematicProjection> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketSchematicProjection::new
    );

    private static final int MAX_BLOCKS = 65536;
    private static final int MAX_BLOCK_ENTITIES = 4096;
    private static final int MAX_ENTITIES = 4096;
    private static final int MAX_NBT_BYTES = 32768;
    private static final int MAX_STATES = 65536;
    private static final int MAX_STATE_LENGTH = 2048;

    public PacketSchematicProjection {
        key = key == null ? "" : key;
        graphId = graphId == null ? "" : graphId;
        dimension = dimension == null ? "" : dimension;
        durationTicks = Math.max(1, durationTicks);
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        states = states == null ? List.of() : List.copyOf(states);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        blockEntities = blockEntities == null ? List.of() : List.copyOf(blockEntities);
        entities = entities == null ? List.of() : List.copyOf(entities);
        if (states.size() > MAX_STATES) {
            throw new IllegalArgumentException("Too many schematic projection states: " + states.size());
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("Too many schematic projection blocks: " + blocks.size());
        }
        if (blockEntities.size() > MAX_BLOCK_ENTITIES) {
            throw new IllegalArgumentException("Too many schematic projection block entities: " + blockEntities.size());
        }
        if (entities.size() > MAX_ENTITIES) {
            throw new IllegalArgumentException("Too many schematic projection entities: " + entities.size());
        }
        for (Block block : blocks) {
            if (block.stateIndex() < 0 || block.stateIndex() >= states.size()) {
                throw new IllegalArgumentException("Invalid schematic projection state index: " + block.stateIndex());
            }
        }
        for (BlockEntity blockEntity : blockEntities) {
            validateNbtSize(blockEntity.tag(), "block entity");
        }
        for (Entity entity : entities) {
            validateNbtSize(entity.tag(), "entity");
        }
    }

    public PacketSchematicProjection(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUtf(32767),
                buf.readUtf(32767),
                buf.readUtf(32767),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readFloat(),
                buf.readBoolean(),
                readStates(buf),
                readBlocks(buf),
                readBlockEntities(buf),
                readEntities(buf)
        );
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(key, 32767);
        buf.writeUtf(graphId, 32767);
        buf.writeUtf(dimension, 32767);
        buf.writeDouble(originX);
        buf.writeDouble(originY);
        buf.writeDouble(originZ);
        buf.writeInt(width);
        buf.writeInt(height);
        buf.writeInt(length);
        buf.writeInt(durationTicks);
        buf.writeFloat(alpha);
        buf.writeBoolean(truncated);
        buf.writeInt(states.size());
        for (String state : states) {
            buf.writeUtf(state == null ? "" : state, MAX_STATE_LENGTH);
        }
        buf.writeInt(blocks.size());
        for (Block block : blocks) {
            block.write(buf);
        }
        buf.writeInt(blockEntities.size());
        for (BlockEntity blockEntity : blockEntities) {
            blockEntity.write(buf);
        }
        buf.writeInt(entities.size());
        for (Entity entity : entities) {
            entity.write(buf);
        }
    }

    private static List<String> readStates(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_STATES) {
            throw new IllegalArgumentException("Invalid schematic projection state count: " + count);
        }
        List<String> states = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            states.add(buf.readUtf(MAX_STATE_LENGTH));
        }
        return states;
    }

    private static List<Block> readBlocks(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_BLOCKS) {
            throw new IllegalArgumentException("Invalid schematic projection block count: " + count);
        }
        List<Block> blocks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blocks.add(new Block(buf));
        }
        return blocks;
    }

    private static List<BlockEntity> readBlockEntities(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_BLOCK_ENTITIES) {
            throw new IllegalArgumentException("Invalid schematic projection block entity count: " + count);
        }
        List<BlockEntity> blockEntities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            blockEntities.add(new BlockEntity(buf));
        }
        return blockEntities;
    }

    private static List<Entity> readEntities(RegistryFriendlyByteBuf buf) {
        int count = buf.readInt();
        if (count < 0 || count > MAX_ENTITIES) {
            throw new IllegalArgumentException("Invalid schematic projection entity count: " + count);
        }
        List<Entity> entities = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entities.add(new Entity(buf));
        }
        return entities;
    }

    private static void validateNbtSize(CompoundTag tag, String label) {
        if (tag != null && tag.sizeInBytes() > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("Schematic projection " + label + " NBT is too large: " + tag.sizeInBytes());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Block(int x, int y, int z, int stateIndex, int color) {
        private Block(RegistryFriendlyByteBuf buf) {
            this(buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt());
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(stateIndex);
            buf.writeInt(color);
        }
    }

    public record BlockEntity(int x, int y, int z, CompoundTag tag) {
        public BlockEntity {
            tag = tag == null ? new CompoundTag() : tag.copy();
            validateNbtSize(tag, "block entity");
        }

        private BlockEntity(RegistryFriendlyByteBuf buf) {
            this(buf.readInt(), buf.readInt(), buf.readInt(), readNbtOrEmpty(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeNbt(tag);
        }
    }

    public record Entity(double x, double y, double z, CompoundTag tag) {
        public Entity {
            tag = tag == null ? new CompoundTag() : tag.copy();
            validateNbtSize(tag, "entity");
        }

        private Entity(RegistryFriendlyByteBuf buf) {
            this(buf.readDouble(), buf.readDouble(), buf.readDouble(), readNbtOrEmpty(buf));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeNbt(tag);
        }
    }

    private static CompoundTag readNbtOrEmpty(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return tag == null ? new CompoundTag() : tag;
    }
}
