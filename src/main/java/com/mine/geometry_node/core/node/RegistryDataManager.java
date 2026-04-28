package com.mine.geometry_node.core.node;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

public class RegistryDataManager {

    // 静态类型

    // --- 懒加载缓存 ---
    private static List<String> BLOCK_CACHE = null;  // 方块
    private static List<String> ITEM_CACHE = null;  // 物品
    private static List<String> ENTITY_TYPE_CACHE = null;  // 实体类型
    private static List<String> EFFECT_CACHE = null;  // 效果
    private static List<String> SOUND_CACHE = null;  // 音效
    private static List<String> PARTICLE_CACHE = null;

    public static List<String> getAllBlocks() {
        if (BLOCK_CACHE == null) {
            BLOCK_CACHE = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(ResourceLocation::toString).sorted().toList();
        }
        return BLOCK_CACHE;
    }

    public static List<String> getAllItems() {
        if (ITEM_CACHE == null) {
            ITEM_CACHE = BuiltInRegistries.ITEM.keySet().stream()
                    .map(ResourceLocation::toString).sorted().toList();
        }
        return ITEM_CACHE;
    }

    public static List<String> getAllEntityTypes() {
        if (ENTITY_TYPE_CACHE == null) {
            ENTITY_TYPE_CACHE = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                    .map(ResourceLocation::toString).sorted().toList();
        }
        return ENTITY_TYPE_CACHE;
    }

    public static List<String> getAllEffects() {
        if (EFFECT_CACHE == null) {
            EFFECT_CACHE = BuiltInRegistries.MOB_EFFECT.keySet().stream()
                    .map(ResourceLocation::toString).sorted().toList();
        }
        return EFFECT_CACHE;
    }

    public static List<String> getAllSounds() {
        if (SOUND_CACHE == null) {
            SOUND_CACHE = BuiltInRegistries.SOUND_EVENT.keySet().stream()
                    .map(net.minecraft.resources.ResourceLocation::toString)
                    .sorted()
                    .toList();
        }
        return SOUND_CACHE;
    }

    public static List<String> getAllParticles() {
        if (PARTICLE_CACHE == null) {
            PARTICLE_CACHE = BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
                    .map(ResourceLocation::toString).sorted().toList();
        }
        return PARTICLE_CACHE;
    }

    // 动态类型

    public static List<String> getDamageTypes(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DAMAGE_TYPE);
    }

    public static List<String> getEnchantments(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.ENCHANTMENT);
    }

    public static List<String> getDimensions(RegistryAccess access) {
        List<String> dims = new java.util.ArrayList<>();

        // 1. 注入我们的特殊作用域
        dims.add("global");

        try {
            if (net.minecraft.client.Minecraft.getInstance().getConnection() != null) {
                List<String> dynamicDims = net.minecraft.client.Minecraft.getInstance().getConnection().levels()
                        .stream()
                        .map(key -> key.location().toString())
                        .sorted()
                        .toList();

                dims.addAll(dynamicDims);
            }
        } catch (NoClassDefFoundError | Exception e) {
            System.err.println("[RegistryDataManager] Fail to get Dimensions from client: " + e.getMessage());
        }

        return dims;
    }

    /**
     * [UI 专用路由] 根据传入的 Registry ID 动态分发并获取数据
     */
    public static List<String> getDynamicOptions(String registryId, RegistryAccess access) {
        if (registryId == null || access == null) return List.of();

        return switch (registryId) {
            case "minecraft:dimension" -> getDimensions(access);
            case "minecraft:enchantment" -> getEnchantments(access);
            case "minecraft:damage_type" -> getDamageTypes(access);
            default -> {
                System.err.println("[RegistryDataManager] 未知的动态注册表 ID: " + registryId);
                yield List.of();
            }
        };
    }

    /**
     * 安全提取指定动态注册表所有 Key，并统一格式化为排序好的字符串列表。
     */
    private static <T> List<String> getDynamicRegistryKeys(RegistryAccess access, ResourceKey<net.minecraft.core.Registry<T>> registryKey) {
        if (access == null) {
            return List.of();
        }

        try {
            var registryOpt = access.registry(registryKey);

            if (registryOpt.isEmpty()) {
                System.err.println("Registry not found for: " + registryKey.location());
                return List.of();
            }

            return registryOpt.get().keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .toList();

        } catch (Exception e) {
            System.err.println("[RegistryDataManager] Failed to fetch dynamic registry: " + registryKey.location());
            e.printStackTrace();
            return List.of();
        }
    }
}