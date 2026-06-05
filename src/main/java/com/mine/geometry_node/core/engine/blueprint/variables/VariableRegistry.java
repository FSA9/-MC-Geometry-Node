package com.mine.geometry_node.core.engine.blueprint.variables;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
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
public class VariableRegistry {

    private static final Map<Class<?>, VariableSerializer<?>> CLASS_TO_SERIALIZER = new HashMap<>();
    private static final Map<String, VariableSerializer<?>> ID_TO_SERIALIZER = new HashMap<>();

    // 包装盒的标准字段名
    private static final String TYPE_KEY = "_gn_type";
    private static final String DATA_KEY = "data";

    public static <T> void register(VariableSerializer<T> serializer) {
        CLASS_TO_SERIALIZER.put(serializer.getTargetClass(), serializer);
        ID_TO_SERIALIZER.put(serializer.getTypeId(), serializer);
    }

    static {
        // UUID
        register(new VariableSerializer<UUID>() {
            @Override public String getTypeId() { return "uuid"; }
            @Override public Class<UUID> getTargetClass() { return UUID.class; }
            @Override public Tag serialize(UUID value) { return NbtUtils.createUUID(value); }
            @Override public UUID deserialize(Tag tag) { return NbtUtils.loadUUID(tag); }
        });

        // BlockPos
        register(new VariableSerializer<BlockPos>() {
            @Override public String getTypeId() { return "block_pos"; }
            @Override public Class<BlockPos> getTargetClass() { return BlockPos.class; }
            @Override public Tag serialize(BlockPos value) { return LongTag.valueOf(value.asLong()); }
            @Override public BlockPos deserialize(Tag tag) { return BlockPos.of(((LongTag) tag).getAsLong()); }
        });

        // Vec3
        register(new VariableSerializer<Vec3>() {
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
                return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
            }
        });

        // BlockState
        register(new VariableSerializer<BlockState>() {
            @Override public String getTypeId() { return "block_state"; }
            @Override public Class<BlockState> getTargetClass() { return BlockState.class; }
            @Override public Tag serialize(BlockState value) { return NbtUtils.writeBlockState(value); }
            @Override public BlockState deserialize(Tag tag) {
                return NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), (CompoundTag) tag);
            }
        });

        // ItemStack
        register(new VariableSerializer<ItemStack>() {
            @Override public String getTypeId() { return "item_stack"; }
            @Override public Class<ItemStack> getTargetClass() { return ItemStack.class; }

            // 旧方法直接返回空或报错即可，因为不会再被调用
            @Override public Tag serialize(ItemStack value) { return null; }
            @Override public ItemStack deserialize(Tag tag) { return null; }

            // 重写带 Provider 的方法
            @Override public Tag serialize(ItemStack value, HolderLookup.Provider provider) {
                return value.saveOptional(provider);
            }
            @Override public ItemStack deserialize(Tag tag, HolderLookup.Provider provider) {
                return ItemStack.parseOptional(provider, (CompoundTag) tag);
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
        for (Map.Entry<Class<?>, VariableSerializer<?>> entry : CLASS_TO_SERIALIZER.entrySet()) {
            if (entry.getKey().isInstance(value)) {
                VariableSerializer<Object> serializer = (VariableSerializer<Object>) entry.getValue();
                CompoundTag wrapper = new CompoundTag();
                wrapper.putString(TYPE_KEY, serializer.getTypeId());
                wrapper.put(DATA_KEY, serializer.serialize(value, provider)); // 传下去
                return wrapper;
            }
        }

        System.err.println("[GeometryNode] Unsupported variable type for saving: " + value.getClass().getName());
        return null;
    }

    @Nullable
    public static Object fromTag(Tag tag, HolderLookup.Provider provider) {
        if (tag == null) return null;

        // --- 快车道 ---
        if (tag instanceof IntTag i) return i.getAsInt();
        if (tag instanceof LongTag l) return l.getAsLong();
        if (tag instanceof ShortTag s) return s.getAsShort();
        if (tag instanceof DoubleTag d) return d.getAsDouble();
        if (tag instanceof FloatTag f) return f.getAsFloat();
        if (tag instanceof StringTag s) return s.getAsString();
        if (tag instanceof ByteTag b) return b.getAsByte() != 0;

        // --- 拆包与慢车道 ---
        if (tag instanceof CompoundTag compound && compound.contains(TYPE_KEY, Tag.TAG_STRING)) {
            String typeId = compound.getString(TYPE_KEY);

            // List
            if ("_gn_list".equals(typeId) && compound.contains(DATA_KEY, Tag.TAG_LIST)) {
                ListTag nbtList = compound.getList(DATA_KEY, Tag.TAG_COMPOUND);
                List<Object> resultList = new ArrayList<>();
                for (int i = 0; i < nbtList.size(); i++) {
                    CompoundTag elementWrapper = nbtList.getCompound(i);
                    resultList.add(fromTag(elementWrapper.get("v"), provider));
                }
                return resultList;
            }
            // Dict
            if ("_gn_dict".equals(typeId) && compound.contains(DATA_KEY, Tag.TAG_COMPOUND)) {
                CompoundTag dataTag = compound.getCompound(DATA_KEY);
                Map<String, Object> resultMap = new HashMap<>();
                for (String key : dataTag.getAllKeys()) {
                    resultMap.put(key, fromTag(dataTag.get(key), provider)); // 递归反序列化 Value
                }
                return resultMap;
            }

            VariableSerializer<?> serializer = ID_TO_SERIALIZER.get(typeId);
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
