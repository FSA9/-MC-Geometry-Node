package com.mine.geometry_node.core.node;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * [注册表数据管理器]
 * 采用代理门面模式 (Facade)，为 UI 渲染或逻辑层提供统一的数据获取接口。
 * 屏蔽底层的物理端差异，并提供优雅的兜底降级处理。
 */
public class RegistryDataManager {

    // 静态类型

    // --- 懒加载缓存 ---
    private static List<String> BLOCK_CACHE = null;  // 方块
    private static List<String> ITEM_CACHE = null;  // 物品
    private static List<String> ENTITY_TYPE_CACHE = null;  // 实体类型
    private static List<String> EFFECT_CACHE = null;  // 效果
    private static List<String> SOUND_CACHE = null;  // 音效

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

    // 动态类型

    public static List<String> getDamageTypes(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DAMAGE_TYPE);
    }

    public static List<String> getEnchantments(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.ENCHANTMENT);
    }

    public static List<String> getDimensions(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DIMENSION);
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