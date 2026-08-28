package com.mine.geometry_node.core.engine.graph.value;

import com.mine.geometry_node.core.node.value.SlotRef;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 变量序列化注册表。
 * 负责将 Java 对象和 NBT Tag 进行双向转换。
 */
public final class GraphValueCodecRegistry {

    private static final Map<Class<?>, GraphValueCodec<?>> CLASS_TO_SERIALIZER = new HashMap<>();
    private static final Map<String, GraphValueCodec<?>> ID_TO_SERIALIZER = new HashMap<>();

    // 包装盒的标准字段名
    private static final String TYPE_KEY = "_gn_type";
    private static final String DATA_KEY = "data";

    private GraphValueCodecRegistry() {
    }

    public static <T> void register(GraphValueCodec<T> serializer) {
        CLASS_TO_SERIALIZER.put(serializer.getTargetClass(), serializer);
        ID_TO_SERIALIZER.put(serializer.getTypeId(), serializer);
    }

    static {
        // UUID
        register(new GraphValueCodec<UUID>() {
            @Override public String getTypeId() { return "uuid"; }
            @Override public Class<UUID> getTargetClass() { return UUID.class; }
            @Override public Tag serialize(UUID value) { return StringTag.valueOf(value.toString()); }
            @Override public UUID deserialize(Tag tag) { return UUID.fromString(tag.asString().orElse("")); }
        });

        // BlockPos
        register(new GraphValueCodec<BlockPos>() {
            @Override public String getTypeId() { return "block_pos"; }
            @Override public Class<BlockPos> getTargetClass() { return BlockPos.class; }
            @Override public Tag serialize(BlockPos value) { return LongTag.valueOf(value.asLong()); }
            @Override public BlockPos deserialize(Tag tag) { return BlockPos.of(((LongTag) tag).longValue()); }
        });

        // Vec3
        register(new GraphValueCodec<Vec3>() {
            @Override public String getTypeId() { return "vec3"; }
            @Override public Class<Vec3> getTargetClass() { return Vec3.class; }
            @Override public Tag serialize(Vec3 value) {
                ListTag list = new ListTag();
                list.add(DoubleTag.valueOf(value.x));
                list.add(DoubleTag.valueOf(value.y));
                list.add(DoubleTag.valueOf(value.z));
                return list;
            }
            @Override public Vec3 deserialize(Tag tag) {
                ListTag list = (ListTag) tag;
                return new Vec3(list.getDoubleOr(0, 0.0), list.getDoubleOr(1, 0.0), list.getDoubleOr(2, 0.0));
            }
        });

        // SlotRef
        register(new GraphValueCodec<SlotRef>() {
            @Override public String getTypeId() { return "slot_ref"; }
            @Override public Class<SlotRef> getTargetClass() { return SlotRef.class; }
            @Override public Tag serialize(SlotRef value) { return StringTag.valueOf(value.serialize()); }
            @Override public SlotRef deserialize(Tag tag) { return SlotRef.parse(tag.asString().orElse("")); }
        });

        // BlockState
        register(new GraphValueCodec<BlockState>() {
            @Override public String getTypeId() { return "block_state"; }
            @Override public Class<BlockState> getTargetClass() { return BlockState.class; }
            @Override public Tag serialize(BlockState value) { return NbtUtils.writeBlockState(value); }
            @Override public BlockState deserialize(Tag tag) {
                return NbtUtils.readBlockState(BuiltInRegistries.BLOCK, (CompoundTag) tag);
            }
        });

        // ItemStack
        register(new GraphValueCodec<ItemStack>() {
            @Override public String getTypeId() { return "item_stack"; }
            @Override public Class<ItemStack> getTargetClass() { return ItemStack.class; }

            // 旧方法直接返回空或报错即可，因为不会再被调用
            @Override public Tag serialize(ItemStack value) { return null; }
            @Override public ItemStack deserialize(Tag tag) { return null; }

            // 重写带 Provider 的方法
            @Override public Tag serialize(ItemStack value, HolderLookup.Provider provider) {
                return ItemStack.OPTIONAL_CODEC
                        .encodeStart(provider.createSerializationContext(NbtOps.INSTANCE), value)
                        .result()
                        .orElseGet(CompoundTag::new);
            }
            @Override public ItemStack deserialize(Tag tag, HolderLookup.Provider provider) {
                return ItemStack.OPTIONAL_CODEC
                        .parse(provider.createSerializationContext(NbtOps.INSTANCE), tag)
                        .result()
                        .orElse(ItemStack.EMPTY);
            }
        });

        register(new GraphValueCodec<EntityTemplateValue>() {
            @Override public String getTypeId() { return "entity_template"; }
            @Override public Class<EntityTemplateValue> getTargetClass() { return EntityTemplateValue.class; }
            @Override public Tag serialize(EntityTemplateValue value) {
                CompoundTag tag = new CompoundTag();
                tag.putString("entity_type", value.entityTypeId());
                tag.put("entity_data", value.data());
                return tag;
            }
            @Override public EntityTemplateValue deserialize(Tag tag) {
                if (!(tag instanceof CompoundTag compound)) return EntityTemplateValue.EMPTY;
                return new EntityTemplateValue(
                        compound.getStringOr("entity_type", ""),
                        compound.getCompoundOrEmpty("entity_data")
                );
            }
        });
    }

    @Nullable
    public static Tag toTag(Object value, HolderLookup.Provider provider) {
        if (value == null) return null;

        if (value instanceof Entity entity) {
            value = entity.getUUID();
        }

        // --- 快车道 ---
        if (value instanceof Integer i) return IntTag.valueOf(i);
        if (value instanceof Long l) return LongTag.valueOf(l);
        if (value instanceof Short s) return ShortTag.valueOf(s);
        if (value instanceof Double d) return DoubleTag.valueOf(d); // 补充 Double
        if (value instanceof Float f) return FloatTag.valueOf(f);
        if (value instanceof String s) return StringTag.valueOf(s);
        if (value instanceof Boolean b) return ByteTag.valueOf(b);

        // List
        if (value instanceof List<?> list) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_list"); // 专用 ID
            ListTag nbtList = new ListTag();

            for (Object item : list) {
                Tag elementTag = toTag(item, provider);
                if (elementTag != null) {
                    // 强制包一层 Compound，打破 ListTag 必须同类型的诅咒
                    CompoundTag elementWrapper = new CompoundTag();
                    elementWrapper.put("v", elementTag);
                    nbtList.add(elementWrapper);
                }
            }
            wrapper.put(DATA_KEY, nbtList);
            return wrapper;
        }

        // Dict
        if (value instanceof Map<?, ?> map) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_dict"); // 专用 ID
            CompoundTag dataTag = new CompoundTag();

            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String key) {
                    Tag elementTag = toTag(entry.getValue(), provider); // 递归序列化 Value
                    if (elementTag != null) {
                        dataTag.put(key, elementTag);
                    }
                }
            }
            wrapper.put(DATA_KEY, dataTag);
            return wrapper;
        }

        // --- 慢车道：多态遍历查找 ---
        for (Map.Entry<Class<?>, GraphValueCodec<?>> entry : CLASS_TO_SERIALIZER.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                GraphValueCodec<Object> serializer = (GraphValueCodec<Object>) entry.getValue();
                CompoundTag wrapper = new CompoundTag();
                wrapper.putString(TYPE_KEY, serializer.getTypeId());
                wrapper.put(DATA_KEY, serializer.serialize(value, provider)); // 传下去
                return wrapper;
            }
        }

        System.err.println("[GeometryNode] Unsupported variable type for saving: " + value.getClass().getName());
        return null;
    }

    /**
     * Encodes a complete value or fails. Unlike the permissive legacy entry point,
     * this method never drops unsupported list elements, map keys, or map values.
     */
    public static Tag toTagStrict(Object value, HolderLookup.Provider provider) {
        if (value == null) {
            throw new IllegalArgumentException("Graph values cannot contain null");
        }
        if (value instanceof List<?> list) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_list");
            ListTag encoded = new ListTag();
            for (Object item : list) {
                CompoundTag element = new CompoundTag();
                element.put("v", toTagStrict(item, provider));
                encoded.add(element);
            }
            wrapper.put(DATA_KEY, encoded);
            return wrapper;
        }
        if (value instanceof Map<?, ?> map) {
            CompoundTag wrapper = new CompoundTag();
            wrapper.putString(TYPE_KEY, "_gn_dict");
            CompoundTag encoded = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Graph map keys must be strings");
                }
                encoded.put(key, toTagStrict(entry.getValue(), provider));
            }
            wrapper.put(DATA_KEY, encoded);
            return wrapper;
        }
        Tag encoded = toTag(value, provider);
        if (encoded == null) {
            throw new IllegalArgumentException("Unsupported graph value type: "
                    + value.getClass().getName());
        }
        return encoded;
    }

    @Nullable
    public static Object fromTag(Tag tag, HolderLookup.Provider provider) {
        if (tag == null) return null;

        // --- 快车道 ---
        if (tag instanceof IntTag i) return i.intValue();
        if (tag instanceof LongTag l) return l.longValue();
        if (tag instanceof ShortTag s) return s.shortValue();
        if (tag instanceof DoubleTag d) return d.doubleValue();
        if (tag instanceof FloatTag f) return f.floatValue();
        if (tag instanceof StringTag s) return s.value();
        if (tag instanceof ByteTag b) return b.byteValue() != 0;

        // --- 拆包与慢车道 ---
        if (tag instanceof CompoundTag compound && compound.contains(TYPE_KEY)) {
            String typeId = compound.getStringOr(TYPE_KEY, "");

            // List
            if ("_gn_list".equals(typeId) && compound.contains(DATA_KEY)) {
                ListTag nbtList = compound.getListOrEmpty(DATA_KEY);
                List<Object> resultList = new ArrayList<>();
                for (int i = 0; i < nbtList.size(); i++) {
                    CompoundTag elementWrapper = nbtList.getCompoundOrEmpty(i);
                    resultList.add(fromTag(elementWrapper.get("v"), provider));
                }
                return resultList;
            }
            // Dict
            if ("_gn_dict".equals(typeId) && compound.contains(DATA_KEY)) {
                CompoundTag dataTag = compound.getCompoundOrEmpty(DATA_KEY);
                Map<String, Object> resultMap = new HashMap<>();
                for (String key : dataTag.keySet()) {
                    resultMap.put(key, fromTag(dataTag.get(key), provider)); // 递归反序列化 Value
                }
                return resultMap;
            }

            GraphValueCodec<?> serializer = ID_TO_SERIALIZER.get(typeId);
            if (serializer != null && compound.contains(DATA_KEY)) {
                return serializer.deserialize(compound.get(DATA_KEY), provider);
            } else {
                System.err.println("[GeometryNode] Missing serializer for type: " + typeId);
            }
        }

        return null;
    }

    public static boolean isSupported(Object value) {
        if (value == null) return false;
        if (value instanceof Entity ||
                value instanceof Integer || value instanceof Long || value instanceof Short ||
                value instanceof Double || value instanceof Float ||
                value instanceof String || value instanceof Boolean ||
                value instanceof List || value instanceof Map) return true;

        for (Class<?> supportedClass : CLASS_TO_SERIALIZER.keySet()) {
            if (supportedClass.isInstance(value)) return true;
        }
        return false;
    }
}
