package com.mine.geometry_node.core.utils.nbt;

import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.*;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 核心数据结构转换器：在节点 ANY/DICT 类型与 Minecraft 原生 NBT 之间进行无损双向转换
 */
public class NbtDictConverter {

    // ==========================================
    // 1. Dict -> NBT (序列化)
    // ==========================================

    public static CompoundTag dictToNbt(Map<String, Object> dict, RegistryAccess registryAccess) {
        CompoundTag tag = new CompoundTag();
        if (dict == null) return tag;

        for (Map.Entry<String, Object> entry : dict.entrySet()) {
            Tag nbtVal = objectToNbt(entry.getValue(), registryAccess);
            if (nbtVal != null) {
                tag.put(entry.getKey(), nbtVal);
            }
        }
        return tag;
    }

    private static Tag objectToNbt(Object val, RegistryAccess registryAccess) {
        if (val == null) return null;

        // 基础数据类型
        if (val instanceof String s) return StringTag.valueOf(s);
        if (val instanceof Integer i) return IntTag.valueOf(i);
        if (val instanceof Float f) return FloatTag.valueOf(f);
        if (val instanceof Double d) return DoubleTag.valueOf(d);
        if (val instanceof Boolean b) return ByteTag.valueOf(b);
        if (val instanceof Long l) return LongTag.valueOf(l);

        // Minecraft 矢量类型
        if (val instanceof Vec3 v) {
            CompoundTag vecTag = new CompoundTag();
            vecTag.putDouble("x", v.x);
            vecTag.putDouble("y", v.y);
            vecTag.putDouble("z", v.z);
            return vecTag;
        }

        // Minecraft 物品堆 (兼容 1.20.5+ 组件)
        if (val instanceof ItemStack itemStack) {
            return ItemStack.OPTIONAL_CODEC
                    .encodeStart(registryAccess.createSerializationContext(NbtOps.INSTANCE), itemStack)
                    .result()
                    .orElseGet(CompoundTag::new);
        }

        // 嵌套字典 (Map)
        if (val instanceof Map<?, ?> map) {
            CompoundTag comp = new CompoundTag();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() instanceof String k) {
                    Tag childTag = objectToNbt(entry.getValue(), registryAccess);
                    if (childTag != null) comp.put(k, childTag);
                }
            }
            return comp;
        }

        // 嵌套列表 (List)
        if (val instanceof List<?> list) {
            ListTag listTag = new ListTag();
            for (Object item : list) {
                Tag childTag = objectToNbt(item, registryAccess);
                if (childTag != null) listTag.add(childTag);
            }
            return listTag;
        }

        // 终极兜底
        return StringTag.valueOf(val.toString());
    }

    // ==========================================
    // 2. NBT -> Dict (反序列化)
    // ==========================================

    public static Map<String, Object> nbtToDict(CompoundTag tag, RegistryAccess registryAccess) {
        Map<String, Object> dict = new HashMap<>();
        if (tag == null) return dict;

        for (String key : tag.keySet()) {
            dict.put(key, nbtToObject(tag.get(key), registryAccess));
        }
        return dict;
    }

    private static Object nbtToObject(Tag tag, RegistryAccess registryAccess) {
        if (tag == null) return null;

        // 复合标签 (CompoundTag) -> 尝试解析为 Vec3, ItemStack, 或退回为常规 Dict
        if (tag instanceof CompoundTag comp) {
            // Heuristic: 检查是不是 Vec3
            if (comp.size() == 3 && comp.contains("x") && comp.contains("y") && comp.contains("z")) {
                try {
                    return new Vec3(comp.getDoubleOr("x", 0.0), comp.getDoubleOr("y", 0.0), comp.getDoubleOr("z", 0.0));
                } catch (Exception ignored) {}
            }

            // Heuristic: 检查是不是 ItemStack (必须包含 id 并且可以被解析)
            if (comp.contains("id") && comp.contains("count")) {
                ItemStack parsedStack = ItemStack.OPTIONAL_CODEC
                        .parse(registryAccess.createSerializationContext(NbtOps.INSTANCE), comp)
                        .result()
                        .orElse(ItemStack.EMPTY);
                if (!parsedStack.isEmpty()) return parsedStack;
            }

            // 否则，普通字典处理
            return nbtToDict(comp, registryAccess);
        }

        // 列表标签 (ListTag) -> 转为 List
        if (tag instanceof ListTag list) {
            List<Object> resultList = new ArrayList<>();
            for (Tag child : list) {
                resultList.add(nbtToObject(child, registryAccess));
            }
            return resultList;
        }

        // 基础数字与布尔
        if (tag instanceof NumericTag num) {
            if (num instanceof DoubleTag) return num.doubleValue();
            if (num instanceof FloatTag) return num.floatValue();
            if (num instanceof IntTag) return num.intValue();
            if (num instanceof LongTag) return num.longValue();
            if (num instanceof ByteTag) return num.byteValue() != 0; // Minecraft 常用 Byte(0/1) 存布尔
            return num.box();
        }

        // 字符串
        if (tag instanceof StringTag s) {
            return s.value();
        }

        return tag.toString();
    }
}
