package com.mine.geometry_node.core.node;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.engine.system.marker.MarkerTypeRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public class RegistryDataManager {
    public static final String DIMENSION_REGISTRY_ID = "minecraft:dimension";
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private RegistryDataManager() {}

    // 静态类型

    // --- 懒加载缓存 ---
    private static volatile List<String> BLOCK_CACHE = null;  // 方块
    private static volatile List<String> ITEM_CACHE = null;  // 物品
    private static volatile List<String> ENTITY_TYPE_CACHE = null;  // 实体类型
    private static volatile List<String> EFFECT_CACHE = null;  // 效果
    private static volatile List<String> SOUND_CACHE = null;  // 音效
    private static volatile List<String> PARTICLE_CACHE = null;
    private static volatile List<String> MENU_CACHE = null;

    public static List<String> getAllBlocks() {
        if (BLOCK_CACHE == null) {
            BLOCK_CACHE = BuiltInRegistries.BLOCK.keySet().stream()
                    .map(id -> id.toString()).sorted().toList();
        }
        return BLOCK_CACHE;
    }

    public static List<String> getAllItems() {
        if (ITEM_CACHE == null) {
            ITEM_CACHE = BuiltInRegistries.ITEM.keySet().stream()
                    .map(id -> id.toString()).sorted().toList();
        }
        return ITEM_CACHE;
    }

    public static List<String> getAllEntityTypes() {
        if (ENTITY_TYPE_CACHE == null) {
            ENTITY_TYPE_CACHE = BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                    .map(id -> id.toString()).sorted().toList();
        }
        return ENTITY_TYPE_CACHE;
    }

    public static List<String> getAllEffects() {
        if (EFFECT_CACHE == null) {
            EFFECT_CACHE = BuiltInRegistries.MOB_EFFECT.keySet().stream()
                    .map(id -> id.toString()).sorted().toList();
        }
        return EFFECT_CACHE;
    }

    public static List<String> getAllSounds() {
        if (SOUND_CACHE == null) {
            SOUND_CACHE = BuiltInRegistries.SOUND_EVENT.keySet().stream()
                    .map(id -> id.toString())
                    .sorted()
                    .toList();
        }
        return SOUND_CACHE;
    }

    public static List<String> getAllParticles() {
        if (PARTICLE_CACHE == null) {
            PARTICLE_CACHE = BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
                    .map(id -> id.toString()).sorted().toList();
        }
        return PARTICLE_CACHE;
    }

    public static List<String> getAllMenus() {
        if (MENU_CACHE == null) {
            MENU_CACHE = BuiltInRegistries.MENU.keySet().stream()
                    .map(id -> id.toString()).sorted().toList();
        }
        return MENU_CACHE;
    }

    public static String[] withEmptyOption(List<String> options) {
        if (options == null || options.isEmpty()) {
            return new String[]{""};
        }

        String[] result = new String[options.size() + 1];
        result[0] = "";
        for (int i = 0; i < options.size(); i++) {
            result[i + 1] = options.get(i);
        }
        return result;
    }

    public static String[] withEmptyOption(String[] options) {
        if (options == null || options.length == 0) {
            return new String[]{""};
        }

        String[] result = new String[options.length + 1];
        result[0] = "";
        System.arraycopy(options, 0, result, 1, options.length);
        return result;
    }

    // 动态类型

    public static List<String> getPortTypes() {
        return java.util.Arrays.stream(PortType.values())
                // 过滤控制流和 ANY
                .filter(t -> !t.isFlow() && t != PortType.ANY)
                .map(Enum::name) // 输出大写名字，如 "FLOAT", "DICT"
                .toList();
    }

    public static List<String> getEntityTypes() {
        return BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(id -> id.toString())
                .sorted()
                .toList();
    }

    public static List<String> getAttributes(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.ATTRIBUTE);
    }

    public static List<String> getDamageTypes(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DAMAGE_TYPE);
    }

    public static List<String> getEnchantments(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.ENCHANTMENT);
    }

    public static List<String> getDimensions(RegistryAccess access) {
        return getDynamicRegistryKeys(access, Registries.DIMENSION);
    }

    @Nullable
    public static ServerLevel resolveDimension(MinecraftServer server, @Nullable Object value) {
        if (server == null) return null;
        String raw = value instanceof String text && !text.isBlank()
                ? text.trim()
                : DEFAULT_DIMENSION;
        Identifier id = Identifier.tryParse(raw);
        if (id == null) return null;
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
    }

    /**
     * [UI 专用路由] 根据传入的 Registry ID 动态分发并获取数据
     */
    public static List<String> getDynamicOptions(String registryId, RegistryAccess access) {
        if (registryId == null) return List.of();

        return switch (registryId) {
            case MultiblockStructureManager.DYNAMIC_REGISTRY_ID -> MultiblockStructureManager.getInstance().getAllIds();
            case DIMENSION_REGISTRY_ID -> access != null ? getDimensions(access) : List.of();
            case "minecraft:enchantment" -> access != null ? getEnchantments(access) : List.of();
            case "minecraft:damage_type" -> access != null ? getDamageTypes(access) : List.of();
            case "minecraft:attribute" -> access != null ? getAttributes(access) : List.of();
            case "minecraft:entity_type" -> getEntityTypes();
            case "minecraft:menu" -> getAllMenus();
            case "geometry_node:port_types" -> getPortTypes();
            case QuestStatusRegistry.DYNAMIC_REGISTRY_ID -> QuestStatusRegistry.INSTANCE.allIds();
            case QuestStatusRegistry.ASSIGNABLE_DYNAMIC_REGISTRY_ID -> QuestStatusRegistry.INSTANCE.assignableIds();
            case MarkerTypeRegistry.DYNAMIC_REGISTRY_ID -> MarkerTypeRegistry.INSTANCE.allIds();
            default -> {
                GeometryNode.LOGGER.warn("[RegistryDataManager] Unknown dynamic registry ID: {}", registryId);
                yield List.of();
            }
        };
    }

    /**
     * Optional translated labels for dynamic option ids. Registries not listed
     * here intentionally display their raw ids.
     */
    public static Map<String, String> getDynamicOptionLabelKeys(String registryId) {
        if (MarkerTypeRegistry.DYNAMIC_REGISTRY_ID.equals(registryId)) {
            return MarkerTypeRegistry.INSTANCE.translationKeys();
        }
        return Map.of();
    }

    /**
     * 安全提取指定动态注册表所有 Key，并统一格式化为排序好的字符串列表。
     */
    private static <T> List<String> getDynamicRegistryKeys(RegistryAccess access, ResourceKey<net.minecraft.core.Registry<T>> registryKey) {
        if (access == null) {
            return List.of();
        }

        try {
            return access.lookupOrThrow(registryKey).keySet().stream()
                    .map(id -> id.toString())
                    .sorted()
                    .toList();

        } catch (Exception e) {
            GeometryNode.LOGGER.error("[RegistryDataManager] Failed to fetch dynamic registry: {}",
                    registryKey.identifier(), e);
            return List.of();
        }
    }
}
