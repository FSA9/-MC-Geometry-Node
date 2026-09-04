package com.mine.geometry_node.core.engine.graph.value;

import com.mine.geometry_node.core.node.value.GraphNumberNormalizer;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.ShortTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Converts graph values to ordinary Minecraft NBT without graph persistence wrappers. */
public final class GraphValueNbtConverter {
    private GraphValueNbtConverter() {
    }

    public static CompoundTag toCompound(Map<String, ?> values, HolderLookup.Provider registries) {
        Objects.requireNonNull(values, "values");
        CompoundTag result = new CompoundTag();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null) throw new IllegalArgumentException("NBT compound keys cannot be null");
            result.put(key, toTag(entry.getValue(), registries));
        }
        return result;
    }

    public static Tag toTag(Object value, HolderLookup.Provider registries) {
        if (value == null) throw new IllegalArgumentException("NBT values cannot be null");
        Objects.requireNonNull(registries, "registries");

        if (value instanceof Tag tag) return tag.copy();
        if (value instanceof Entity entity) return uuidTag(entity.getUUID());
        if (value instanceof UUID uuid) return uuidTag(uuid);
        if (value instanceof Boolean bool) return ByteTag.valueOf(bool);
        if (value instanceof Byte number) return ByteTag.valueOf(number);
        if (value instanceof Short number) return ShortTag.valueOf(number);
        if (value instanceof Integer number) return IntTag.valueOf(number);
        if (value instanceof Long number) return LongTag.valueOf(number);
        if (value instanceof Float number) return FloatTag.valueOf(number);
        if (value instanceof Double number) return DoubleTag.valueOf(number);
        if (value instanceof Number number) {
            return toTag(GraphNumberNormalizer.normalize(number), registries);
        }
        if (value instanceof String string) return StringTag.valueOf(string);
        if (value instanceof Item item) {
            return StringTag.valueOf(BuiltInRegistries.ITEM.getKey(item).toString());
        }
        if (value instanceof ItemStack stack) {
            return ItemStack.OPTIONAL_CODEC
                    .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                    .getOrThrow(IllegalArgumentException::new);
        }
        if (value instanceof BlockState state) return NbtUtils.writeBlockState(state);
        if (value instanceof Vec3 vector) {
            CompoundTag result = new CompoundTag();
            result.putDouble("x", vector.x);
            result.putDouble("y", vector.y);
            result.putDouble("z", vector.z);
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            CompoundTag result = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("NBT compound keys must be strings");
                }
                result.put(key, toTag(entry.getValue(), registries));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            ListTag result = new ListTag();
            for (Object element : list) {
                Tag encoded = toTag(element, registries);
                if (!result.addTag(result.size(), encoded)) {
                    throw new IllegalArgumentException("Minecraft NBT lists must contain one tag type");
                }
            }
            return result;
        }
        throw new IllegalArgumentException("Unsupported vanilla NBT value: " + value.getClass().getName());
    }

    private static IntArrayTag uuidTag(UUID value) {
        return new IntArrayTag(UUIDUtil.uuidToIntArray(value));
    }
}
