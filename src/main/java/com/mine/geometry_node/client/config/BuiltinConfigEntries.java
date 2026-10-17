package com.mine.geometry_node.client.config;

import com.mine.geometry_node.client.input.binding.InputBinding;
import com.mine.geometry_node.client.input.binding.ControlKeyBinding;
import com.mine.geometry_node.client.input.binding.KeyBinding;
import com.mine.geometry_node.client.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.input.binding.KeyScope;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Stable built-in setting handles. Default literals remain owned by {@link AppConfig}. */
public final class BuiltinConfigEntries {
    public static final ConfigCategory VIEWPORT = category("viewport", 100);
    public static final ConfigCategory GAME_VIEW = category("game_view", 150);
    public static final ConfigCategory GAME_VIEW_GENERAL = childCategory("game_view_general", 100, GAME_VIEW);
    public static final ConfigCategory NODE = category("node", 200);
    public static final ConfigCategory ASSET_BROWSER = category("asset_browser", 300);
    public static final ConfigCategory PREVIEW_CACHE = category("preview_cache", 375);
    public static final ConfigCategory SHORTCUT_GLOBAL = category("shortcut_global", 400);
    public static final ConfigCategory SHORTCUT_VIEWPORT = category("shortcut_viewport", 500);
    public static final ConfigCategory SHORTCUT_GAME_VIEW = category("shortcut_game_view", 550);
    public static final ConfigCategory SHORTCUT_GAME_VIEW_GENERAL = childCategory("shortcut_game_view_general", 100, SHORTCUT_GAME_VIEW);
    public static final ConfigCategory SHORTCUT_GAME_VIEW_GAME = new ConfigCategory(
            "shortcut_game_view_game", "geometry_node.settings.category.shortcut_game_view_game", 200,
            SHORTCUT_GAME_VIEW, "geometry_node.settings.category.shortcut_game_view_game.description");
    public static final ConfigCategory SHORTCUT_GAME_VIEW_EDIT = childCategory("shortcut_game_view_edit", 300, SHORTCUT_GAME_VIEW);
    public static final ConfigCategory SHORTCUT_GAME_VIEW_FLIGHT = childCategory("shortcut_game_view_flight", 400, SHORTCUT_GAME_VIEW);
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
    public static final ConfigEntry<Integer> GAME_VIEW_BACKGROUND_FPS = integer(
            "gameView.backgroundFps", GAME_VIEW_GENERAL, 100, 1, 120, ConfigEntry.SettingsVisibility.VISIBLE,
            config -> config.gameView.backgroundFps, (config, value) -> config.gameView.backgroundFps = value);
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
    public static final ConfigEntry<String> GAME_VIEW_PROJECTION = key("keyBindings.gameView.toggleProjection", SHORTCUT_GAME_VIEW_EDIT, 200, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.toggleProjection, (config, value) -> config.keyBindings.gameView.toggleProjection = value);
    public static final ConfigEntry<String> GAME_VIEW_SIDEBAR = key("keyBindings.gameView.toggleSidebar", SHORTCUT_GAME_VIEW_GENERAL, 300, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.toggleSidebar, (config, value) -> config.keyBindings.gameView.toggleSidebar = value);

    public static final ConfigEntry<String> GAME_VIEW_MENU = key("keyBindings.gameView.viewMenu", SHORTCUT_GAME_VIEW_GENERAL, 350, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.viewMenu, (config, value) -> config.keyBindings.gameView.viewMenu = value);

    public static final ConfigEntry<String> GAME_VIEW_SNAP = key("keyBindings.gameView.toggleSnap", SHORTCUT_GAME_VIEW_EDIT, 465, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.toggleSnap, (config,value) -> config.keyBindings.gameView.toggleSnap = value);

    public static final ConfigEntry<String> GAME_VIEW_MOVE = key("keyBindings.gameView.move", SHORTCUT_GAME_VIEW_EDIT, 470, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.move, (config,value) -> config.keyBindings.gameView.move = value);
    public static final ConfigEntry<String> GAME_VIEW_ROTATE = key("keyBindings.gameView.rotate", SHORTCUT_GAME_VIEW_EDIT, 480, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.rotate, (config,value) -> config.keyBindings.gameView.rotate = value);
    public static final ConfigEntry<String> GAME_VIEW_SCALE = key("keyBindings.gameView.scale", SHORTCUT_GAME_VIEW_EDIT, 490, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.scale, (config,value) -> config.keyBindings.gameView.scale = value);
    public static final ConfigEntry<String> GAME_VIEW_AXIS_X = key("keyBindings.gameView.axisX", SHORTCUT_GAME_VIEW_EDIT, 500, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.axisX, (config,value) -> config.keyBindings.gameView.axisX = value);
    public static final ConfigEntry<String> GAME_VIEW_AXIS_Y = key("keyBindings.gameView.axisY", SHORTCUT_GAME_VIEW_EDIT, 510, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.axisY, (config,value) -> config.keyBindings.gameView.axisY = value);
    public static final ConfigEntry<String> GAME_VIEW_AXIS_Z = key("keyBindings.gameView.axisZ", SHORTCUT_GAME_VIEW_EDIT, 520, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.axisZ, (config,value) -> config.keyBindings.gameView.axisZ = value);
    public static final ConfigEntry<String> GAME_VIEW_CONFIRM = key("keyBindings.gameView.confirm", SHORTCUT_GAME_VIEW_EDIT, 530, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.confirm, (config,value) -> config.keyBindings.gameView.confirm = value);

    public static final ConfigEntry<String> GAME_VIEW_ADD = key("keyBindings.gameView.add", SHORTCUT_GAME_VIEW_EDIT, 400, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.add, (config, value) -> config.keyBindings.gameView.add = value);
    public static final ConfigEntry<String> GAME_VIEW_FOCUS = key("keyBindings.gameView.focus", SHORTCUT_GAME_VIEW_EDIT, 410, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.focus, (config, value) -> config.keyBindings.gameView.focus = value);
    public static final ConfigEntry<String> GAME_VIEW_ENTITY_MOVE = ConfigEntry.builder(
            "keyBindings.gameView.entityMove", SHORTCUT_GAME_VIEW_EDIT, ConfigEntry.EditorType.SHORTCUT,
            (AppConfig config) -> config.keyBindings.gameView.entityMove, (config, value) -> config.keyBindings.gameView.entityMove = value)
            .label(labelKey("keyBindings.gameView.entityMove")).description(descriptionKey("keyBindings.gameView.entityMove"))
            .order(412).keyScope(KeyScope.GAME_VIEW).normalize(BuiltinConfigEntries::normalizeShortcut).build();
    public static final ConfigEntry<String> GAME_VIEW_COPY = key("keyBindings.gameView.copy", SHORTCUT_GAME_VIEW_EDIT, 415, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.copy, (config, value) -> config.keyBindings.gameView.copy = value);
    public static final ConfigEntry<String> GAME_VIEW_PASTE = key("keyBindings.gameView.paste", SHORTCUT_GAME_VIEW_EDIT, 416, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.paste, (config, value) -> config.keyBindings.gameView.paste = value);
    public static final ConfigEntry<String> GAME_VIEW_DELETE = key("keyBindings.gameView.delete", SHORTCUT_GAME_VIEW_EDIT, 420, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.delete, (config, value) -> config.keyBindings.gameView.delete = value);
    public static final ConfigEntry<String> GAME_VIEW_UNDO = key("keyBindings.gameView.undo", SHORTCUT_GAME_VIEW_EDIT, 430, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.undo, (config, value) -> config.keyBindings.gameView.undo = value);
    public static final ConfigEntry<String> GAME_VIEW_REDO = key("keyBindings.gameView.redo", SHORTCUT_GAME_VIEW_EDIT, 440, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.redo, (config, value) -> config.keyBindings.gameView.redo = value);
    public static final ConfigEntry<String> GAME_VIEW_CANCEL = key("keyBindings.gameView.cancel", SHORTCUT_GAME_VIEW_EDIT, 450, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.cancel, (config, value) -> config.keyBindings.gameView.cancel = value);
    public static final ConfigEntry<String> GAME_VIEW_SELECT_ALL = key("keyBindings.gameView.selectAll", SHORTCUT_GAME_VIEW_EDIT, 460, KeyScope.GAME_VIEW,
            config -> config.keyBindings.gameView.selectAll, (config, value) -> config.keyBindings.gameView.selectAll = value);

    public static final ConfigEntry<String> GAME_FLIGHT_FORWARD = controlKey("keyBindings.gameView.forward", 400, true,
            config -> config.keyBindings.gameView.forward, (config, value) -> config.keyBindings.gameView.forward = value);
    public static final ConfigEntry<String> GAME_FLIGHT_BACKWARD = controlKey("keyBindings.gameView.backward", 500, true,
            config -> config.keyBindings.gameView.backward, (config, value) -> config.keyBindings.gameView.backward = value);
    public static final ConfigEntry<String> GAME_FLIGHT_LEFT = controlKey("keyBindings.gameView.left", 600, true,
            config -> config.keyBindings.gameView.left, (config, value) -> config.keyBindings.gameView.left = value);
    public static final ConfigEntry<String> GAME_FLIGHT_RIGHT = controlKey("keyBindings.gameView.right", 700, true,
            config -> config.keyBindings.gameView.right, (config, value) -> config.keyBindings.gameView.right = value);
    public static final ConfigEntry<String> GAME_FLIGHT_UP = controlKey("keyBindings.gameView.up", 800, true,
            config -> config.keyBindings.gameView.up, (config, value) -> config.keyBindings.gameView.up = value);
    public static final ConfigEntry<String> GAME_FLIGHT_DOWN = controlKey("keyBindings.gameView.down", 900, true,
            config -> config.keyBindings.gameView.down, (config, value) -> config.keyBindings.gameView.down = value);
    public static final ConfigEntry<String> GAME_FLIGHT_EXIT = controlKey("keyBindings.gameView.exit", 1000, false,
            config -> config.keyBindings.gameView.exit, (config, value) -> config.keyBindings.gameView.exit = value);

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
            VIEWPORT_GRID_SIZE, VIEWPORT_SNAP_TO_GRID, VIEWPORT_SHOW_GRID_AND_AXIS, GAME_VIEW_BACKGROUND_FPS,
            NODE_CORNER_RADIUS, ASSET_QUICK_ACCESS_PATHS, ASSET_VIEW_MODE,
            PREVIEW_MAX_SIZE_MIB, PREVIEW_LOCATION,
            GLOBAL_UNDO, GLOBAL_REDO, GLOBAL_SAVE, GLOBAL_COPY, GLOBAL_PASTE, GLOBAL_CUT, GLOBAL_DELETE, GLOBAL_RENAME,
            VIEWPORT_SELECT_ALL, VIEWPORT_DELETE, VIEWPORT_TOGGLE_SNAP, VIEWPORT_TOGGLE_GRID, VIEWPORT_TOGGLE_SIDEBAR,
            VIEWPORT_MOVE, VIEWPORT_GROUP_FRAME, VIEWPORT_GROUP_NODE, SHOP_CLEAR_SLOT,
            GAME_VIEW_PROJECTION, GAME_VIEW_SIDEBAR, GAME_VIEW_MENU,
            GAME_VIEW_SNAP, GAME_VIEW_MOVE, GAME_VIEW_ROTATE, GAME_VIEW_SCALE, GAME_VIEW_AXIS_X, GAME_VIEW_AXIS_Y, GAME_VIEW_AXIS_Z, GAME_VIEW_CONFIRM,
            GAME_VIEW_ADD, GAME_VIEW_FOCUS, GAME_VIEW_ENTITY_MOVE, GAME_VIEW_COPY, GAME_VIEW_PASTE, GAME_VIEW_DELETE, GAME_VIEW_UNDO, GAME_VIEW_REDO, GAME_VIEW_CANCEL, GAME_VIEW_SELECT_ALL,
            GAME_FLIGHT_FORWARD, GAME_FLIGHT_BACKWARD, GAME_FLIGHT_LEFT, GAME_FLIGHT_RIGHT, GAME_FLIGHT_UP, GAME_FLIGHT_DOWN, GAME_FLIGHT_EXIT);

    private BuiltinConfigEntries() {}

    static void register(ConfigRegistry registry) {
        for (ConfigCategory category : List.of(VIEWPORT, GAME_VIEW, NODE, ASSET_BROWSER, PREVIEW_CACHE,
                SHORTCUT_GLOBAL, SHORTCUT_VIEWPORT, SHORTCUT_GAME_VIEW, SHORTCUT_GAME_VIEW_GAME, SHORTCUT_SHOP)) {
            registry.registerCategory(category);
        }
        for (ConfigEntry<?> entry : ALL) registerUnchecked(registry, entry);
    }

    private static ConfigCategory childCategory(String id, int order, ConfigCategory parent) {
        return new ConfigCategory(id, "geometry_node.settings.category." + id, order, parent);
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

    private static ConfigEntry<String> controlKey(String id, int order, boolean allowUnassigned,
                                                   java.util.function.Function<AppConfig, String> getter,
                                                   java.util.function.BiConsumer<AppConfig, String> setter) {
        return ConfigEntry.builder(id, SHORTCUT_GAME_VIEW_FLIGHT, ConfigEntry.EditorType.KEY_BINDING, getter, setter)
                .label(labelKey(id)).description(descriptionKey(id)).order(order)
                .keyScope(KeyScope.GAME_VIEW_FLIGHT).captureControlKeys(true)
                .normalize(value -> {
                    if (value != null && value.isBlank()) return allowUnassigned ? "" : null;
                    ControlKeyBinding binding = ControlKeyBinding.parse(value);
                    return binding != null ? binding.text() : null;
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
