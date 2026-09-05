package com.mine.geometry_node.client.ui.persistence.config;

import com.mine.geometry_node.client.ui.persistence.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.ui.shortcut.KeyScope;
import com.mine.geometry_node.core.engine.system.asset.transfer.config.AssetTransferConfigKeys;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Stable built-in setting handles. Default literals remain owned by {@link AppConfig}. */
public final class BuiltinConfigEntries {
    public static final ConfigCategory VIEWPORT = category("viewport", 100);
    public static final ConfigCategory NODE = category("node", 200);
    public static final ConfigCategory ASSET_BROWSER = category("asset_browser", 300);
    public static final ConfigCategory NETWORK_TRANSFER = category("network_transfer", 350);
    public static final ConfigCategory PREVIEW_CACHE = category("preview_cache", 375);
    public static final ConfigCategory SHORTCUT_GLOBAL = category("shortcut_global", 400);
    public static final ConfigCategory SHORTCUT_VIEWPORT = category("shortcut_viewport", 500);
    public static final ConfigCategory SHORTCUT_SHOP = category("shortcut_shop", 600);

    public static final ConfigEntry<Integer> VIEWPORT_GRID_SIZE = integer(
            "viewport.gridSize", VIEWPORT, 100, 1, 500, ConfigEntry.SettingsVisibility.HIDDEN,
            config -> config.viewport.gridSize, (config, value) -> config.viewport.gridSize = value);
    public static final ConfigEntry<Boolean> VIEWPORT_SNAP_TO_GRID = bool(
            "viewport.snapToGrid", VIEWPORT, 200, ConfigEntry.SettingsVisibility.HIDDEN,
            config -> config.viewport.snapToGrid, (config, value) -> config.viewport.snapToGrid = value);
    public static final ConfigEntry<Boolean> VIEWPORT_SHOW_GRID_AND_AXIS = bool(
            "viewport.showGridAndAxis", VIEWPORT, 300, ConfigEntry.SettingsVisibility.HIDDEN,
            config -> config.viewport.showGridAndAxis, (config, value) -> config.viewport.showGridAndAxis = value);
    public static final ConfigEntry<Float> NODE_CORNER_RADIUS = floating(
            "node.cornerRadius", NODE, 100, 0.0f, 24.0f, 0.5f, ConfigEntry.SettingsVisibility.HIDDEN,
            config -> config.node.cornerRadius, (config, value) -> config.node.cornerRadius = value);
    public static final ConfigEntry<List<String>> ASSET_QUICK_ACCESS_PATHS = ConfigEntry
            .<List<String>>builder("assetBrowser.quickAccessPaths", ASSET_BROWSER, ConfigEntry.EditorType.PATH_LIST,
                    config -> config.assetBrowser.quickAccessPaths,
                    (config, value) -> config.assetBrowser.quickAccessPaths = new ArrayList<>(value))
            .label(labelKey("assetBrowser.quickAccessPaths"))
            .description(descriptionKey("assetBrowser.quickAccessPaths"))
            .order(100)
            .normalize(BuiltinConfigEntries::normalizeQuickAccessPaths)
            .build();
    public static final ConfigEntry<String> ASSET_VIEW_MODE = ConfigEntry
            .<String>builder("assetBrowser.viewMode", ASSET_BROWSER, ConfigEntry.EditorType.CHOICE,
                    config -> config.assetBrowser.viewMode, (config, value) -> config.assetBrowser.viewMode = value)
            .label(labelKey("assetBrowser.viewMode"))
            .description(descriptionKey("assetBrowser.viewMode"))
            .order(200)
            .choices(List.of("LIST", "ICON_SMALL", "ICON_MEDIUM", "ICON_LARGE"))
            .choiceTranslationKeys(Map.of(
                    "LIST", "geometry_node.settings.choice.asset_view.list",
                    "ICON_SMALL", "geometry_node.settings.choice.asset_view.icon_small",
                    "ICON_MEDIUM", "geometry_node.settings.choice.asset_view.icon_medium",
                    "ICON_LARGE", "geometry_node.settings.choice.asset_view.icon_large"))
            .settingsVisibility(ConfigEntry.SettingsVisibility.HIDDEN)
            .normalize(value -> List.of("LIST", "ICON_SMALL", "ICON_MEDIUM", "ICON_LARGE").contains(value) ? value : null)
            .build();

    public static final ConfigEntry<Integer> TRANSFER_MAX_UPLOAD_FILE_MIB = integer(
            AssetTransferConfigKeys.clientId(AssetTransferConfigKeys.MAX_UPLOAD_FILE_SIZE_MIB), NETWORK_TRANSFER, 100, 1, 2048,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.maxUploadFileSizeMiB,
            (config, value) -> config.networkTransfer.maxUploadFileSizeMiB = value);
    public static final ConfigEntry<Integer> TRANSFER_MAX_DOWNLOAD_FILE_MIB = integer(
            AssetTransferConfigKeys.clientId(AssetTransferConfigKeys.MAX_DOWNLOAD_FILE_SIZE_MIB), NETWORK_TRANSFER, 200, 1, 2048,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.maxDownloadFileSizeMiB,
            (config, value) -> config.networkTransfer.maxDownloadFileSizeMiB = value);
    public static final ConfigEntry<Integer> TRANSFER_CHUNK_SIZE_KIB = integer(
            AssetTransferConfigKeys.clientId(AssetTransferConfigKeys.CHUNK_SIZE_KIB), NETWORK_TRANSFER, 300, 4, 24,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.chunkSizeKiB,
            (config, value) -> config.networkTransfer.chunkSizeKiB = value);
    public static final ConfigEntry<Integer> TRANSFER_UPLOAD_RATE_KIBPS = integer(
            AssetTransferConfigKeys.clientId(AssetTransferConfigKeys.UPLOAD_RATE_LIMIT_KIBPS), NETWORK_TRANSFER, 400, 0, 1_048_576,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.uploadRateLimitKiBps,
            (config, value) -> config.networkTransfer.uploadRateLimitKiBps = value);
    public static final ConfigEntry<Integer> TRANSFER_DOWNLOAD_RATE_KIBPS = integer(
            AssetTransferConfigKeys.clientId(AssetTransferConfigKeys.DOWNLOAD_RATE_LIMIT_KIBPS), NETWORK_TRANSFER, 500, 0, 1_048_576,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.downloadRateLimitKiBps,
            (config, value) -> config.networkTransfer.downloadRateLimitKiBps = value);
    public static final ConfigEntry<Integer> TRANSFER_COMPLETED_HISTORY_LIMIT = integer(
            "networkTransfer.completedHistoryLimit", NETWORK_TRANSFER, 600, 0, 1000,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.completedHistoryLimit,
            (config, value) -> config.networkTransfer.completedHistoryLimit = value);
    public static final ConfigEntry<Integer> TRANSFER_FAILED_HISTORY_LIMIT = integer(
            "networkTransfer.failedHistoryLimit", NETWORK_TRANSFER, 700, 0, 1000,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.networkTransfer.failedHistoryLimit,
            (config, value) -> config.networkTransfer.failedHistoryLimit = value);
    public static final ConfigEntry<Integer> PREVIEW_MAX_SIZE_MIB = integer(
            "previewCache.maxSizeMiB", PREVIEW_CACHE, 100, 32, 16_384,
            ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.previewCache.maxSizeMiB,
            (config, value) -> config.previewCache.maxSizeMiB = value);
    public static final ConfigEntry<String> PREVIEW_LOCATION = ConfigEntry
            .<String>builder("previewCache.location", PREVIEW_CACHE, ConfigEntry.EditorType.PATH,
                    config -> config.previewCache.location,
                    (config, value) -> config.previewCache.location = value)
            .label(labelKey("previewCache.location"))
            .description(descriptionKey("previewCache.location"))
            .order(200)
            .settingsVisibility(ConfigEntry.SettingsVisibility.VISIBLE)
            .normalize(BuiltinConfigEntries::normalizePreviewLocation)
            .build();
    public static final ConfigEntry<String> GLOBAL_UNDO = key("keyBindings.global.undo", SHORTCUT_GLOBAL, 100, KeyScope.GLOBAL,
            config -> config.keyBindings.global.undo, (config, value) -> config.keyBindings.global.undo = value);
    public static final ConfigEntry<String> GLOBAL_REDO = key("keyBindings.global.redo", SHORTCUT_GLOBAL, 200, KeyScope.GLOBAL,
            config -> config.keyBindings.global.redo, (config, value) -> config.keyBindings.global.redo = value);
    public static final ConfigEntry<String> GLOBAL_SAVE = key("keyBindings.global.save", SHORTCUT_GLOBAL, 300, KeyScope.GLOBAL,
            config -> config.keyBindings.global.save, (config, value) -> config.keyBindings.global.save = value);
    public static final ConfigEntry<String> GLOBAL_COPY = key("keyBindings.global.copy", SHORTCUT_GLOBAL, 400, KeyScope.GLOBAL,
            config -> config.keyBindings.global.copy, (config, value) -> config.keyBindings.global.copy = value);
    public static final ConfigEntry<String> GLOBAL_PASTE = key("keyBindings.global.paste", SHORTCUT_GLOBAL, 500, KeyScope.GLOBAL,
            config -> config.keyBindings.global.paste, (config, value) -> config.keyBindings.global.paste = value);
    public static final ConfigEntry<String> GLOBAL_CUT = key("keyBindings.global.cut", SHORTCUT_GLOBAL, 600, KeyScope.GLOBAL,
            config -> config.keyBindings.global.cut, (config, value) -> config.keyBindings.global.cut = value);
    public static final ConfigEntry<String> GLOBAL_DELETE = key("keyBindings.global.delete", SHORTCUT_GLOBAL, 700, KeyScope.GLOBAL,
            config -> config.keyBindings.global.delete, (config, value) -> config.keyBindings.global.delete = value);
    public static final ConfigEntry<String> GLOBAL_RENAME = key("keyBindings.global.rename", SHORTCUT_GLOBAL, 800, KeyScope.GLOBAL,
            config -> config.keyBindings.global.rename, (config, value) -> config.keyBindings.global.rename = value);

    public static final ConfigEntry<String> VIEWPORT_DELETE = key("keyBindings.viewport.delete", SHORTCUT_VIEWPORT, 100, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.delete, (config, value) -> config.keyBindings.viewport.delete = value);
    public static final ConfigEntry<String> VIEWPORT_SELECT_ALL = key("keyBindings.viewport.selectAll", SHORTCUT_VIEWPORT, 50, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.selectAll, (config, value) -> config.keyBindings.viewport.selectAll = value);
    public static final ConfigEntry<String> VIEWPORT_TOGGLE_SNAP = key("keyBindings.viewport.toggleSnapToGrid", SHORTCUT_VIEWPORT, 200, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.toggleSnapToGrid, (config, value) -> config.keyBindings.viewport.toggleSnapToGrid = value);
    public static final ConfigEntry<String> VIEWPORT_TOGGLE_GRID = key("keyBindings.viewport.toggleGridAndAxis", SHORTCUT_VIEWPORT, 300, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.toggleGridAndAxis, (config, value) -> config.keyBindings.viewport.toggleGridAndAxis = value);
    public static final ConfigEntry<String> VIEWPORT_TOGGLE_SIDEBAR = key("keyBindings.viewport.toggleRightSidebar", SHORTCUT_VIEWPORT, 400, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.toggleRightSidebar, (config, value) -> config.keyBindings.viewport.toggleRightSidebar = value);
    public static final ConfigEntry<String> VIEWPORT_MOVE = key("keyBindings.viewport.moveSelection", SHORTCUT_VIEWPORT, 500, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.moveSelection, (config, value) -> config.keyBindings.viewport.moveSelection = value);
    public static final ConfigEntry<String> VIEWPORT_GROUP_FRAME = key("keyBindings.viewport.groupIntoFrame", SHORTCUT_VIEWPORT, 600, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.groupIntoFrame, (config, value) -> config.keyBindings.viewport.groupIntoFrame = value);
    public static final ConfigEntry<String> VIEWPORT_GROUP_NODE = key("keyBindings.viewport.groupIntoNodeGroup", SHORTCUT_VIEWPORT, 700, KeyScope.VIEWPORT,
            config -> config.keyBindings.viewport.groupIntoNodeGroup, (config, value) -> config.keyBindings.viewport.groupIntoNodeGroup = value);
    public static final ConfigEntry<String> SHOP_CLEAR_SLOT = ConfigEntry
            .<String>builder("keyBindings.shopEditor.clearSlot", SHORTCUT_SHOP, ConfigEntry.EditorType.SHORTCUT,
                    config -> config.keyBindings.shopEditor.clearSlot,
                    (config, value) -> config.keyBindings.shopEditor.clearSlot = value)
            .label(labelKey("keyBindings.shopEditor.clearSlot"))
            .description(descriptionKey("keyBindings.shopEditor.clearSlot"))
            .order(100)
            .keyScope(KeyScope.SHOP_EDITOR)
            .normalize(BuiltinConfigEntries::normalizeShortcut)
            .build();

    private static final List<ConfigEntry<?>> ALL = List.of(
            VIEWPORT_GRID_SIZE, VIEWPORT_SNAP_TO_GRID, VIEWPORT_SHOW_GRID_AND_AXIS,
            NODE_CORNER_RADIUS, ASSET_QUICK_ACCESS_PATHS, ASSET_VIEW_MODE,
            TRANSFER_MAX_UPLOAD_FILE_MIB, TRANSFER_MAX_DOWNLOAD_FILE_MIB, TRANSFER_CHUNK_SIZE_KIB,
            TRANSFER_UPLOAD_RATE_KIBPS, TRANSFER_DOWNLOAD_RATE_KIBPS,
            TRANSFER_COMPLETED_HISTORY_LIMIT, TRANSFER_FAILED_HISTORY_LIMIT,
            PREVIEW_MAX_SIZE_MIB, PREVIEW_LOCATION,
            GLOBAL_UNDO, GLOBAL_REDO, GLOBAL_SAVE, GLOBAL_COPY, GLOBAL_PASTE, GLOBAL_CUT, GLOBAL_DELETE, GLOBAL_RENAME,
            VIEWPORT_SELECT_ALL, VIEWPORT_DELETE, VIEWPORT_TOGGLE_SNAP, VIEWPORT_TOGGLE_GRID, VIEWPORT_TOGGLE_SIDEBAR,
            VIEWPORT_MOVE, VIEWPORT_GROUP_FRAME, VIEWPORT_GROUP_NODE, SHOP_CLEAR_SLOT);

    private BuiltinConfigEntries() {}

    static void register(ConfigRegistry registry) {
        for (ConfigCategory category : List.of(VIEWPORT, NODE, ASSET_BROWSER, NETWORK_TRANSFER, PREVIEW_CACHE,
                SHORTCUT_GLOBAL, SHORTCUT_VIEWPORT, SHORTCUT_SHOP)) {
            registry.registerCategory(category);
        }
        for (ConfigEntry<?> entry : ALL) registerUnchecked(registry, entry);
    }

    private static ConfigCategory category(String id, int order) {
        return new ConfigCategory(id, "geometry_node.settings.category." + id, order);
    }

    private static ConfigEntry<Boolean> bool(String id, ConfigCategory category, int order,
                                               ConfigEntry.SettingsVisibility visibility,
                                               java.util.function.Function<AppConfig, Boolean> getter,
                                               java.util.function.BiConsumer<AppConfig, Boolean> setter) {
        return ConfigEntry.builder(id, category, ConfigEntry.EditorType.BOOLEAN, getter, setter)
                .label(labelKey(id)).description(descriptionKey(id)).order(order)
                .settingsVisibility(visibility).build();
    }

    private static ConfigEntry<Integer> integer(String id, ConfigCategory category, int order, int min, int max,
                                                 ConfigEntry.SettingsVisibility visibility,
                                                 java.util.function.Function<AppConfig, Integer> getter,
                                                 java.util.function.BiConsumer<AppConfig, Integer> setter) {
        return ConfigEntry.builder(id, category, ConfigEntry.EditorType.INTEGER, getter, setter)
                .label(labelKey(id)).description(descriptionKey(id)).order(order).range(min, max, 1)
                .settingsVisibility(visibility)
                .normalize(value -> value != null && value >= min && value <= max ? value : null).build();
    }

    private static ConfigEntry<Float> floating(String id, ConfigCategory category, int order,
                                                float min, float max, float step,
                                                ConfigEntry.SettingsVisibility visibility,
                                                java.util.function.Function<AppConfig, Float> getter,
                                                java.util.function.BiConsumer<AppConfig, Float> setter) {
        return ConfigEntry.builder(id, category, ConfigEntry.EditorType.FLOAT, getter, setter)
                .label(labelKey(id)).description(descriptionKey(id)).order(order).range(min, max, step)
                .settingsVisibility(visibility)
                .normalize(value -> value != null && Float.isFinite(value) && value >= min && value <= max ? value : null).build();
    }

    private static ConfigEntry<String> key(String id, ConfigCategory category, int order, KeyScope scope,
                                            java.util.function.Function<AppConfig, String> getter,
                                            java.util.function.BiConsumer<AppConfig, String> setter) {
        return ConfigEntry.builder(id, category, ConfigEntry.EditorType.KEY_BINDING, getter, setter)
                .label(labelKey(id)).description(descriptionKey(id)).order(order).keyScope(scope)
                .normalize(value -> {
                    if (value != null && value.isBlank()) return "";
                    KeyBinding binding = KeyBinding.parse(value);
                    return binding != null ? binding.text : null;
                }).build();
    }

    private static List<String> normalizeQuickAccessPaths(List<String> paths) {
        if (paths == null) return null;
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String path : paths) {
            if (!AssetBrowserPathPolicy.canPersistQuickAccessPath(path)) continue;
            String value = AssetBrowserPathPolicy.toConfigPath(AssetBrowserPathPolicy.resolveConfigPath(path));
            if (!value.isBlank()) normalized.add(value);
        }
        return new ArrayList<>(normalized);
    }

    private static String normalizeShortcut(String value) {
        if (value != null && value.isBlank()) return "";
        InputBinding binding = InputBinding.parse(value);
        return binding != null ? binding.text() : null;
    }

    private static String normalizePreviewLocation(String value) {
        if (value == null || value.isBlank()) return null;
        return AssetBrowserPathPolicy.toConfigPath(AssetBrowserPathPolicy.resolveConfigPath(value.trim()));
    }

    private static String labelKey(String id) {
        return "geometry_node.settings.entry." + id + ".label";
    }

    private static String descriptionKey(String id) {
        return "geometry_node.settings.entry." + id + ".description";
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerUnchecked(ConfigRegistry registry, ConfigEntry<?> entry) {
        registry.register((ConfigEntry) entry);
    }
}
