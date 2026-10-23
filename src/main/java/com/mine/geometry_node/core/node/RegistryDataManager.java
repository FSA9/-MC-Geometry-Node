package com.mine.geometry_node.core.node;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.engine.blueprint.multiblock.MultiblockStructureManager;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.engine.system.marker.type.MarkerTypeRegistry;
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
import java.time.Duration;
import java.util.function.Supplier;
import java.util.concurrent.CompletableFuture;
import com.mine.geometry_node.core.cache.*;

public class RegistryDataManager {
    public static final String DIMENSION_REGISTRY_ID = "minecraft:dimension";
    public static final String DEFAULT_DIMENSION = "minecraft:overworld";

    private RegistryDataManager() {}

    // Only immutable built-in registry names are cached. World/dynamic registries stay live.
    private static final BoundedCache<String, List<String>> BUILTIN_NAMES =
            new BoundedCache<>(7, Long.MAX_VALUE, Duration.ZERO, ignored -> 1, ignored -> {});

    static {
        CacheRegistry.GLOBAL.register(new CacheDescriptor("geometry_node:registry_names",
                "geometry_node.cache.registry_names", CacheDescriptor.Domain.COMMON,
                CacheDescriptor.Storage.MEMORY, CacheDescriptor.Lifetime.APPLICATION), new ManagedCache() {
            public java.util.concurrent.CompletionStage<CacheStats> stats() {
                return CompletableFuture.completedFuture(CacheStats.memory(BUILTIN_NAMES.size(), -1));
            }
            public java.util.concurrent.CompletionStage<Void> clear(CacheClearRequest request) {
                BUILTIN_NAMES.clear();
                return CompletableFuture.completedFuture(null);
            }
            public boolean supports(CacheClearRequest request) {
                return request.namespace() == null && request.reason() == CacheClearRequest.Reason.USER;
            }
        });
    }

    private static List<String> names(String key, Supplier<List<String>> loader) {
        long generation = BUILTIN_NAMES.generation();
        List<String> names = BUILTIN_NAMES.get(key);
        if (names != null) return names;
        List<String> loaded = loader.get();
        synchronized (BUILTIN_NAMES) {
            names = BUILTIN_NAMES.get(key);
            if (names != null) return names;
            BUILTIN_NAMES.putIfCurrent(key, loaded, generation);
        }
        // A concurrent clear never invalidates the caller's immutable snapshot.
        return loaded;
    }

    public static List<String> getAllBlocks() {
        return names("blocks", () -> BuiltInRegistries.BLOCK.keySet().stream()
                .map(Object::toString).sorted().toList());
    }

    public static List<String> getAllItems() {
        return names("items", () -> BuiltInRegistries.ITEM.keySet().stream()
                .map(Object::toString).sorted().toList());
    }

    public static List<String> getAllEntityTypes() {
        return names("entities", () -> BuiltInRegistries.ENTITY_TYPE.keySet().stream()
                .map(Object::toString).sorted().toList());
    }

    public static List<String> getAllEffects() {
        return names("effects", () -> BuiltInRegistries.MOB_EFFECT.keySet().stream()
                .map(Object::toString).sorted().toList());
    }

    public static List<String> getAllSounds() {
        return names("sounds", () -> BuiltInRegistries.SOUND_EVENT.keySet().stream()
                .map(Object::toString).sorted().toList());
    }

    public static List<String> getAllParticles() {
        return names("particles", () -> BuiltInRegistries.PARTICLE_TYPE.keySet().stream()
                .map(Object::toString).sorted().toList());
    }

    public static List<String> getAllMenus() {
        return names("menus", () -> BuiltInRegistries.MENU.keySet().stream()
                .map(Object::toString).sorted().toList());
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
        return getAllEntityTypes();
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
        if (QuestStatusRegistry.DYNAMIC_REGISTRY_ID.equals(registryId)
                || QuestStatusRegistry.ASSIGNABLE_DYNAMIC_REGISTRY_ID.equals(registryId)) {
            return QuestStatusRegistry.INSTANCE.translationKeys();
        }
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
