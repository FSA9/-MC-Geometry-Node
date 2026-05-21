package com.mine.geometry_node.core.utils;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

public class ItemCodecUtils {
    private static final Gson GSON = new Gson();

    /**
     * 将 ItemStack 序列化为扁平的 JSON 字符串
     */
    public static String toJson(ItemStack stack, HolderLookup.Provider registries) {
        if (stack == null || stack.isEmpty()) {
            return "";
        }
        try {
            // 1.21 必须使用 RegistryOps 注入注册表上下文，否则无法解析附魔、自定义名字等高级组件
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            JsonElement jsonElement = ItemStack.CODEC.encodeStart(ops, stack)
                    .getOrThrow(IllegalStateException::new);

            return GSON.toJson(jsonElement);
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    /**
     * 将 JSON 字符串反序列化还原为原汁原味的 ItemStack
     */
    public static ItemStack fromJson(String jsonStr, HolderLookup.Provider registries) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            JsonElement jsonElement = JsonParser.parseString(jsonStr);
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);

            return ItemStack.CODEC.parse(ops, jsonElement)
                    .getOrThrow(IllegalStateException::new);
        } catch (Exception e) {
            e.printStackTrace();
            return ItemStack.EMPTY;
        }
    }
}