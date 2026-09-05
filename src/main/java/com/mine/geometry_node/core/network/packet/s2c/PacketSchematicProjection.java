package com.mine.geometry_node.core.network.packet.s2c;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public record PacketSchematicProjection(
        String resourceId,
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
            PacketSchematicProjection::read
    );

    public static final int MAX_STATES = 65_536;
    public static final int MAX_BLOCKS = 262_144;
    public static final int MAX_BLOCK_ENTITIES = 16_384;
    public static final int MAX_ENTITIES = 4_096;
    public static final int MAX_DIMENSION_AXIS = 4_096;
    public static final long MAX_VOLUME = 268_435_456L;
    public static final int MAX_DURATION_TICKS = 72_000;
    private static final int MAX_IDENTIFIER_LENGTH = 1_024;
    public static final int MAX_STATE_LENGTH = 2_048;
    public static final long MAX_TOTAL_TEXT_BYTES = 8L * 1024L * 1024L;
    public static final int MAX_NBT_BYTES = 32 * 1024;
    public static final long MAX_TOTAL_NBT_BYTES = 16L * 1024L * 1024L;

    public PacketSchematicProjection {
        resourceId = resourceId == null ? "" : resourceId;
        graphId = graphId == null ? "" : graphId;
        dimension = dimension == null ? "" : dimension;
        validateText(resourceId, MAX_IDENTIFIER_LENGTH, "resource ID");
        validateText(graphId, MAX_IDENTIFIER_LENGTH, "graph ID");
        validateText(dimension, MAX_IDENTIFIER_LENGTH, "dimension");
        durationTicks = Math.clamp(durationTicks, 1, MAX_DURATION_TICKS);
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));
        states = normalizeStates(states);
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        blockEntities = blockEntities == null ? List.of() : List.copyOf(blockEntities);
        entities = entities == null ? List.of() : List.copyOf(entities);
        validateCount(states.size(), MAX_STATES, "state");
        validateCount(blocks.size(), MAX_BLOCKS, "block");
        validateCount(blockEntities.size(), MAX_BLOCK_ENTITIES, "block entity");
        validateCount(entities.size(), MAX_ENTITIES, "entity");
        validateDimensions(width, height, length, states, blocks, blockEntities, entities);
        for (String state : states) {
            validateText(state, MAX_STATE_LENGTH, "block state");
        }
        validateTextBudget(resourceId, graphId, dimension, states);
        for (Block block : blocks) {
            if (block.stateIndex() < 0 || block.stateIndex() >= states.size()) {
                throw new IllegalArgumentException("Invalid schematic projection state index: " + block.stateIndex());
            }
        }
        validateNbt(blockEntities, entities);
    }

    private static PacketSchematicProjection read(RegistryFriendlyByteBuf buf) {
        TextBudget textBudget = new TextBudget(MAX_TOTAL_TEXT_BYTES);
        String resourceId = readText(buf, MAX_IDENTIFIER_LENGTH, "resource ID", textBudget);
        String graphId = readText(buf, MAX_IDENTIFIER_LENGTH, "graph ID", textBudget);
        String dimension = readText(buf, MAX_IDENTIFIER_LENGTH, "dimension", textBudget);
        double originX = buf.readDouble();
        double originY = buf.readDouble();
        double originZ = buf.readDouble();
        int width = buf.readInt();
        int height = buf.readInt();
        int length = buf.readInt();
        int durationTicks = buf.readInt();
        float alpha = buf.readFloat();
        boolean truncated = buf.readBoolean();
        List<String> states = readStates(buf, textBudget);
        List<Block> blocks = readBlocks(buf);
        NbtBudget nbtBudget = new NbtBudget(MAX_TOTAL_NBT_BYTES);
        List<BlockEntity> blockEntities = readBlockEntities(buf, nbtBudget);
        List<Entity> entities = readEntities(buf, nbtBudget);
        return new PacketSchematicProjection(resourceId, graphId, dimension,
                originX, originY, originZ, width, height, length, durationTicks, alpha, truncated,
                states, blocks, blockEntities, entities);
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(resourceId, MAX_IDENTIFIER_LENGTH);
        buf.writeUtf(graphId, MAX_IDENTIFIER_LENGTH);
        buf.writeUtf(dimension, MAX_IDENTIFIER_LENGTH);
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

    public static PacketSchematicProjection removal(String resourceId, String graphId, String dimension) {
        return new PacketSchematicProjection(resourceId, graphId, dimension,
                0.0D, 0.0D, 0.0D, 0, 0, 0, 1, 0.0F,
                false, List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> readStates(RegistryFriendlyByteBuf buf, TextBudget textBudget) {
        int count = readCount(buf, MAX_STATES, "state");
        List<String> states = new ArrayList<>(initialCapacity(count));
        for (int i = 0; i < count; i++) {
            states.add(readText(buf, MAX_STATE_LENGTH, "block state", textBudget));
        }
        return states;
    }

    private static List<Block> readBlocks(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_BLOCKS, "block");
        List<Block> blocks = new ArrayList<>(initialCapacity(count));
        for (int i = 0; i < count; i++) {
            blocks.add(new Block(buf));
        }
        return blocks;
    }

    private static List<BlockEntity> readBlockEntities(RegistryFriendlyByteBuf buf, NbtBudget nbtBudget) {
        int count = readCount(buf, MAX_BLOCK_ENTITIES, "block entity");
        List<BlockEntity> blockEntities = new ArrayList<>(initialCapacity(count));
        for (int i = 0; i < count; i++) {
            blockEntities.add(new BlockEntity(buf, nbtBudget));
        }
        return blockEntities;
    }

    private static List<Entity> readEntities(RegistryFriendlyByteBuf buf, NbtBudget nbtBudget) {
        int count = readCount(buf, MAX_ENTITIES, "entity");
        List<Entity> entities = new ArrayList<>(initialCapacity(count));
        for (int i = 0; i < count; i++) {
            entities.add(new Entity(buf, nbtBudget));
        }
        return entities;
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int maximum, String label) {
        int count = buf.readInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid schematic projection " + label + " count: " + count);
        }
        return count;
    }

    private static int initialCapacity(int count) {
        return Math.min(count, 1024);
    }

    private static void validateNbtSize(CompoundTag tag, String label) {
        if (tag != null && tag.sizeInBytes() > MAX_NBT_BYTES) {
            throw new IllegalArgumentException("Schematic projection " + label + " NBT is too large: " + tag.sizeInBytes());
        }
    }

    private static void validateNbt(List<BlockEntity> blockEntities, List<Entity> entities) {
        long totalBytes = 0L;
        for (BlockEntity blockEntity : blockEntities) {
            validateNbtSize(blockEntity.tag(), "block entity");
            if (blockEntity.tag() != null) totalBytes += blockEntity.tag().sizeInBytes();
        }
        for (Entity entity : entities) {
            validateNbtSize(entity.tag(), "entity");
            if (entity.tag() != null) totalBytes += entity.tag().sizeInBytes();
        }
        if (totalBytes > MAX_TOTAL_NBT_BYTES) {
            throw new IllegalArgumentException("Schematic projection total NBT is too large: " + totalBytes);
        }
    }

    private static void validateCount(int count, int maximum, String label) {
        if (count > maximum) {
            throw new IllegalArgumentException("Schematic projection " + label + " count exceeds limit " + maximum);
        }
    }

    private static void validateText(String value, int maximum, String label) {
        if (value.length() > maximum) {
            throw new IllegalArgumentException("Schematic projection " + label + " exceeds limit " + maximum);
        }
    }

    private static List<String> normalizeStates(List<String> states) {
        if (states == null || states.isEmpty()) return List.of();
        List<String> normalized = new ArrayList<>(states.size());
        for (String state : states) {
            normalized.add(state == null ? "" : state);
        }
        return List.copyOf(normalized);
    }

    private static String readText(RegistryFriendlyByteBuf buf, int maximum, String label, TextBudget budget) {
        String value = buf.readUtf(maximum);
        budget.consume(utf8Bytes(value), label);
        return value;
    }

    private static void validateTextBudget(String resourceId, String graphId, String dimension, List<String> states) {
        TextBudget budget = new TextBudget(MAX_TOTAL_TEXT_BYTES);
        budget.consume(utf8Bytes(resourceId), "resource ID");
        budget.consume(utf8Bytes(graphId), "graph ID");
        budget.consume(utf8Bytes(dimension), "dimension");
        for (String state : states) {
            budget.consume(utf8Bytes(state), "block state");
        }
    }

    private static long utf8Bytes(String value) {
        long bytes = 0L;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 0x80) {
                bytes++;
            } else if (character < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(character)
                    && index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1))) {
                bytes += 4;
                index++;
            } else if (Character.isSurrogate(character)) {
                bytes++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private static void validateDimensions(int width, int height, int length,
                                           List<String> states, List<Block> blocks,
                                           List<BlockEntity> blockEntities, List<Entity> entities) {
        boolean removal = width == 0 && height == 0 && length == 0
                && states.isEmpty() && blocks.isEmpty() && blockEntities.isEmpty() && entities.isEmpty();
        if (removal) return;
        if (width <= 0 || height <= 0 || length <= 0
                || width > MAX_DIMENSION_AXIS || height > MAX_DIMENSION_AXIS || length > MAX_DIMENSION_AXIS) {
            throw new IllegalArgumentException("Invalid schematic projection dimensions: "
                    + width + "x" + height + "x" + length);
        }
        long volume = (long) width * height * length;
        if (volume > MAX_VOLUME) {
            throw new IllegalArgumentException("Schematic projection volume is too large: " + volume);
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

        private BlockEntity(RegistryFriendlyByteBuf buf, NbtBudget nbtBudget) {
            this(buf.readInt(), buf.readInt(), buf.readInt(), nbtBudget.read(buf, "block entity"));
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

        private Entity(RegistryFriendlyByteBuf buf, NbtBudget nbtBudget) {
            this(buf.readDouble(), buf.readDouble(), buf.readDouble(), nbtBudget.read(buf, "entity"));
        }

        private void write(RegistryFriendlyByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeNbt(tag);
        }
    }

    private static final class TextBudget {
        private long remainingBytes;

        private TextBudget(long maximumBytes) {
            this.remainingBytes = maximumBytes;
        }

        private void consume(long bytes, String label) {
            if (bytes > remainingBytes) {
                throw new IllegalArgumentException("Schematic projection total text exceeds byte budget at " + label);
            }
            remainingBytes -= bytes;
        }
    }

    private static final class NbtBudget {
        private long remainingBytes;

        private NbtBudget(long maximumBytes) {
            this.remainingBytes = maximumBytes;
        }

        private CompoundTag read(RegistryFriendlyByteBuf buf, String label) {
            long itemQuota = Math.min(MAX_NBT_BYTES, remainingBytes);
            Tag decoded = buf.readNbt(NbtAccounter.create(itemQuota));
            CompoundTag tag;
            if (decoded == null) {
                tag = new CompoundTag();
            } else if (decoded instanceof CompoundTag compoundTag) {
                tag = compoundTag;
            } else {
                throw new IllegalArgumentException("Schematic projection " + label + " NBT is not a compound tag");
            }
            validateNbtSize(tag, label);
            long bytes = tag.sizeInBytes();
            if (bytes > remainingBytes) {
                throw new IllegalArgumentException("Schematic projection total NBT exceeds byte budget at " + label);
            }
            remainingBytes -= bytes;
            return tag;
        }
    }
}
