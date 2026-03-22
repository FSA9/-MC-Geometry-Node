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

    /**
     * 获取当前世界加载的所有【伤害类型】(动态数据包驱动)
     * * @param access 所在世界的注册表上下文
     * (客户端: Minecraft.getInstance().level.registryAccess() )
     * (服务端: serverLevel.registryAccess() )
     * @return 按字母排序的字符串列表 (例如: "minecraft:fall", "minecraft:player")
     */
    public static List<String> getDamageTypes(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DAMAGE_TYPE);
    }

    // 获取附魔
    public static List<String> getEnchantments(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.ENCHANTMENT);
    }

    // 获取维度
    public static List<String> getDimensions(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DIMENSION);
    }

    // ==========================================
    // 内部通用提取逻辑
    // ==========================================

    /**
     * 安全提取指定动态注册表的所有 Key，并统一格式化为排序好的字符串列表。
     */
    private static <T> List<String> getDynamicRegistryKeys(RegistryAccess access, ResourceKey<net.minecraft.core.Registry<T>> registryKey) {
        if (access == null) {
            return List.of();
        }

        try {
            // 2. 核心细节：使用 .registry() 返回 Optional，绝不要用 .registryOrThrow()
            // 因为如果某个 Mod 损坏了注册表，或者环境异常，OrThrow 会直接让游戏闪退。
            var registryOpt = access.registry(registryKey);

            if (registryOpt.isEmpty()) {
                System.err.println("Registry not found for: " + registryKey.location());
                return List.of();
            }

            // 3. 提取、转换、排序、收集流水线
            return registryOpt.get().keySet().stream()
                    .map(ResourceLocation::toString) // 转为标准的 "modid:name" 格式
                    .sorted()                        // 首字母自然排序，方便玩家在 UI 下拉框里找
                    .toList();                       // 收集为不可变的高效列表

        } catch (Exception e) {
            // 4. 终极兜底：即便底层抛出了不可预知的异常（如并发修改），也要保全大局
            System.err.println("[RegistryDataManager] Failed to fetch dynamic registry: " + registryKey.location());
            e.printStackTrace();
            return List.of();
        }
    }
}