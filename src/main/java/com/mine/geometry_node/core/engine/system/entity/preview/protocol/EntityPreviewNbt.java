package com.mine.geometry_node.core.engine.system.entity.preview.protocol;

import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.buffer.Unpooled;
import net.minecraft.nbt.*;

import java.io.IOException;

/** Bounds are checked before recursive copies and writes, and before allocations on reads. */
public final class EntityPreviewNbt {
    private EntityPreviewNbt() {}

    public static void validate(Tag tag) { new Budget().visit(tag, 0); }

    public static byte[] encode(CompoundTag tag) {
        validate(tag);
        var buffer = Unpooled.buffer(256, EntityPreviewLimits.MAX_APPEARANCE_BYTES);
        try {
            NbtIo.write(tag, new ByteBufOutputStream(buffer));
            byte[] result = new byte[buffer.readableBytes()];
            buffer.readBytes(result);
            return result;
        } catch (IOException | IndexOutOfBoundsException failure) {
            throw new IllegalArgumentException("Entity appearance byte limit exceeded", failure);
        } finally { buffer.release(); }
    }

    public static CompoundTag decode(byte[] bytes) {
        if (bytes.length > EntityPreviewLimits.MAX_APPEARANCE_BYTES)
            throw new IllegalArgumentException("Entity appearance byte limit exceeded");
        var buffer = Unpooled.wrappedBuffer(bytes);
        try {
            CompoundTag result = NbtIo.read(new ByteBufInputStream(buffer),
                    new NbtAccounter(EntityPreviewLimits.MAX_NBT_HEAP_BYTES, EntityPreviewLimits.MAX_NBT_DEPTH));
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing appearance data");
            validate(result);
            return result;
        } catch (IOException failure) {
            throw new IllegalArgumentException("Invalid entity appearance NBT", failure);
        } finally { buffer.release(); }
    }

    private static final class Budget {
        int nodes, bytes;
        void charge(int amount) {
            bytes += amount;
            if (bytes > EntityPreviewLimits.MAX_APPEARANCE_BYTES)
                throw new IllegalArgumentException("Entity appearance byte limit exceeded");
        }
        void string(String value) {
            if (value.length() > EntityPreviewLimits.MAX_STRING_LENGTH)
                throw new IllegalArgumentException("Entity appearance string limit exceeded");
            // Modified UTF-8 is bounded above by three bytes per UTF-16 code unit.
            charge(2 + 3 * value.length());
        }
        void length(int length) {
            if (length > EntityPreviewLimits.MAX_COLLECTION_LENGTH)
                throw new IllegalArgumentException("Entity appearance collection limit exceeded");
        }
        void visit(Tag tag, int depth) {
            if (depth >= EntityPreviewLimits.MAX_NBT_DEPTH || ++nodes > EntityPreviewLimits.MAX_NBT_NODES)
                throw new IllegalArgumentException("Entity appearance complexity limit exceeded");
            charge(9);
            if (tag instanceof CompoundTag compound) {
                length(compound.size());
                for (var entry : compound.entrySet()) { string(entry.getKey()); visit(entry.getValue(), depth + 1); }
            } else if (tag instanceof ListTag list) {
                length(list.size());
                for (Tag value : list) visit(value, depth + 1);
            } else if (tag instanceof StringTag string) string(string.value());
            else if (tag instanceof ByteArrayTag array) { length(array.getAsByteArray().length); charge(array.getAsByteArray().length); }
            else if (tag instanceof IntArrayTag array) { length(array.getAsIntArray().length); charge(4 * array.getAsIntArray().length); }
            else if (tag instanceof LongArrayTag array) { length(array.getAsLongArray().length); charge(8 * array.getAsLongArray().length); }
            else if (tag instanceof NumericTag number && !Double.isFinite(number.doubleValue()))
                throw new IllegalArgumentException("Nonfinite entity appearance number");
        }
    }
}
