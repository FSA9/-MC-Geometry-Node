package com.mine.geometry_node.client.ui.persistence.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ConfigSanitizer {
    private static final List<String> VIEW_MODES = List.of("LIST", "ICON_SMALL", "ICON_MEDIUM", "ICON_LARGE");

    private static final int GRID_SIZE_MIN = 1;
    private static final int GRID_SIZE_MAX = 500;
    private static final float CORNER_RADIUS_MIN = 0.0f;
    private static final float CORNER_RADIUS_MAX = 24.0f;

    private ConfigSanitizer() {
    }

    static boolean looksLikeConfig(JsonObject root) {
        return root != null && (root.has("assetBrowser") || root.has("viewport") || root.has("node") || root.has("keyBindings"));
    }

    static Result fromJson(JsonObject root) {
        AppConfig defaults = ConfigDefaults.create();
        AppConfig config = new AppConfig();
        boolean changed = false;

        JsonObject assetBrowser = readObject(root, "assetBrowser");
        if (assetBrowser == null) {
            config.assetBrowser = defaults.assetBrowser;
            changed = true;
        } else {
            ReadList paths = readStringList(assetBrowser, "quickAccessPaths");
            if (paths.valid) {
                NormalizeList normalized = normalizeQuickAccessPaths(paths.value);
                config.assetBrowser.quickAccessPaths = normalized.value;
                changed |= paths.changed || normalized.changed;
            } else {
                config.assetBrowser.quickAccessPaths = defaults.assetBrowser.quickAccessPaths;
                changed = true;
            }

            ReadList favorites = readStringList(assetBrowser, "favoriteGraphPaths");
            if (favorites.valid) {
                NormalizeList normalized = normalizeFavoriteGraphPaths(favorites.value);
                config.assetBrowser.favoriteGraphPaths = normalized.value;
                changed |= favorites.changed || normalized.changed;
            } else {
                config.assetBrowser.favoriteGraphPaths = defaults.assetBrowser.favoriteGraphPaths;
                changed = true;
            }

            ReadString viewMode = readString(assetBrowser, "viewMode");
            if (viewMode.valid && VIEW_MODES.contains(viewMode.value)) {
                config.assetBrowser.viewMode = viewMode.value;
                changed |= viewMode.changed;
            } else {
                config.assetBrowser.viewMode = defaults.assetBrowser.viewMode;
                changed = true;
            }
        }

        JsonObject viewport = readObject(root, "viewport");
        if (viewport == null) {
            config.viewport = defaults.viewport;
            changed = true;
        } else {
            ReadInt gridSize = readInt(viewport, "gridSize");
            if (gridSize.valid && gridSize.value >= GRID_SIZE_MIN && gridSize.value <= GRID_SIZE_MAX) {
                config.viewport.gridSize = gridSize.value;
                changed |= gridSize.changed;
            } else {
                config.viewport.gridSize = defaults.viewport.gridSize;
                changed = true;
            }

            ReadBoolean snapToGrid = readBoolean(viewport, "snapToGrid");
            if (snapToGrid.valid) {
                config.viewport.snapToGrid = snapToGrid.value;
                changed |= snapToGrid.changed;
            } else {
                config.viewport.snapToGrid = defaults.viewport.snapToGrid;
                changed = true;
            }

            ReadBoolean showGridAndAxis = readBoolean(viewport, "showGridAndAxis");
            if (showGridAndAxis.valid) {
                config.viewport.showGridAndAxis = showGridAndAxis.value;
                changed |= showGridAndAxis.changed;
            } else {
                config.viewport.showGridAndAxis = defaults.viewport.showGridAndAxis;
                changed = true;
            }
        }

        JsonObject node = readObject(root, "node");
        if (node == null) {
            config.node = defaults.node;
            changed = true;
        } else {
            ReadFloat cornerRadius = readFloat(node, "cornerRadius");
            if (cornerRadius.valid && Float.isFinite(cornerRadius.value)
                    && cornerRadius.value >= CORNER_RADIUS_MIN && cornerRadius.value <= CORNER_RADIUS_MAX) {
                config.node.cornerRadius = cornerRadius.value;
                changed |= cornerRadius.changed;
            } else {
                config.node.cornerRadius = defaults.node.cornerRadius;
                changed = true;
            }
        }

        JsonObject keyBindings = readObject(root, "keyBindings");
        changed |= readKeyBindings(keyBindings, config, defaults);

        return new Result(config, changed);
    }

    static Result sanitize(AppConfig config) {
        if (config == null) return new Result(ConfigDefaults.create(), true);

        boolean changed = false;
        AppConfig defaults = ConfigDefaults.create();

        if (config.assetBrowser == null) {
            config.assetBrowser = defaults.assetBrowser;
            changed = true;
        } else {
            if (config.assetBrowser.quickAccessPaths == null) {
                config.assetBrowser.quickAccessPaths = defaults.assetBrowser.quickAccessPaths;
                changed = true;
            } else {
                NormalizeList normalized = normalizeQuickAccessPaths(config.assetBrowser.quickAccessPaths);
                if (normalized.changed) {
                    config.assetBrowser.quickAccessPaths = normalized.value;
                    changed = true;
                }
            }

            if (config.assetBrowser.favoriteGraphPaths == null) {
                config.assetBrowser.favoriteGraphPaths = defaults.assetBrowser.favoriteGraphPaths;
                changed = true;
            } else {
                NormalizeList normalized = normalizeFavoriteGraphPaths(config.assetBrowser.favoriteGraphPaths);
                if (normalized.changed) {
                    config.assetBrowser.favoriteGraphPaths = normalized.value;
                    changed = true;
                }
            }

            if (!VIEW_MODES.contains(config.assetBrowser.viewMode)) {
                config.assetBrowser.viewMode = defaults.assetBrowser.viewMode;
                changed = true;
            }
        }

        if (config.viewport == null) {
            config.viewport = defaults.viewport;
            changed = true;
        } else {
            if (config.viewport.gridSize < GRID_SIZE_MIN || config.viewport.gridSize > GRID_SIZE_MAX) {
                config.viewport.gridSize = defaults.viewport.gridSize;
                changed = true;
            }
        }

        if (config.node == null) {
            config.node = defaults.node;
            changed = true;
        } else {
            if (!Float.isFinite(config.node.cornerRadius)
                    || config.node.cornerRadius < CORNER_RADIUS_MIN || config.node.cornerRadius > CORNER_RADIUS_MAX) {
                config.node.cornerRadius = defaults.node.cornerRadius;
                changed = true;
            }
        }

        if (config.keyBindings == null) {
            config.keyBindings = defaults.keyBindings;
            changed = true;
        } else {
            changed |= sanitizeKeyBindings(config.keyBindings, defaults.keyBindings);
        }

        return new Result(config, changed);
    }

    private static boolean readKeyBindings(JsonObject keyBindings, AppConfig config, AppConfig defaults) {
        if (keyBindings == null) {
            config.keyBindings = defaults.keyBindings;
            return true;
        }

        boolean changed = false;
        config.keyBindings = new AppConfig.KeyBindingsConfig();

        JsonObject global = readObject(keyBindings, "global");
        JsonObject viewport = readObject(keyBindings, "viewport");
        JsonObject shopEditor = readObject(keyBindings, "shopEditor");
        JsonObject legacy = keyBindings;

        changed |= global == null || viewport == null || shopEditor == null;
        changed |= hasAny(keyBindings,
                "undo", "redo", "save", "copy", "paste", "delete",
                "toggleSnapToGrid", "toggleGridAndAxis", "groupIntoFrame",
                "groupIntoNodeGroup", "moveSelection", "shopEditorClearSlot");

        ReadKeyBinding undo = readKeyBindingWithLegacy(global, legacy, "undo", defaults.keyBindings.global.undo);
        config.keyBindings.global.undo = undo.value;
        changed |= undo.changed;

        ReadKeyBinding redo = readKeyBindingWithLegacy(global, legacy, "redo", defaults.keyBindings.global.redo);
        config.keyBindings.global.redo = redo.value;
        changed |= redo.changed;

        ReadKeyBinding save = readKeyBindingWithLegacy(global, legacy, "save", defaults.keyBindings.global.save);
        config.keyBindings.global.save = save.value;
        changed |= save.changed;

        ReadKeyBinding copy = readKeyBindingWithLegacy(global, legacy, "copy", defaults.keyBindings.global.copy);
        config.keyBindings.global.copy = copy.value;
        changed |= copy.changed;

        ReadKeyBinding paste = readKeyBindingWithLegacy(global, legacy, "paste", defaults.keyBindings.global.paste);
        config.keyBindings.global.paste = paste.value;
        changed |= paste.changed;

        ReadKeyBinding delete = readKeyBindingWithLegacy(viewport, legacy, "delete", defaults.keyBindings.viewport.delete);
        config.keyBindings.viewport.delete = delete.value;
        changed |= delete.changed;

        ReadKeyBinding toggleSnapToGrid = readKeyBindingWithLegacy(viewport, legacy, "toggleSnapToGrid", defaults.keyBindings.viewport.toggleSnapToGrid);
        config.keyBindings.viewport.toggleSnapToGrid = toggleSnapToGrid.value;
        changed |= toggleSnapToGrid.changed;

        ReadKeyBinding toggleGridAndAxis = readKeyBindingWithLegacy(viewport, legacy, "toggleGridAndAxis", defaults.keyBindings.viewport.toggleGridAndAxis);
        config.keyBindings.viewport.toggleGridAndAxis = toggleGridAndAxis.value;
        changed |= toggleGridAndAxis.changed;

        ReadKeyBinding groupIntoFrame = readKeyBindingWithLegacy(viewport, legacy, "groupIntoFrame", defaults.keyBindings.viewport.groupIntoFrame);
        config.keyBindings.viewport.groupIntoFrame = groupIntoFrame.value;
        changed |= groupIntoFrame.changed;

        ReadKeyBinding groupIntoNodeGroup = readKeyBindingWithLegacy(viewport, legacy, "groupIntoNodeGroup", defaults.keyBindings.viewport.groupIntoNodeGroup);
        config.keyBindings.viewport.groupIntoNodeGroup = groupIntoNodeGroup.value;
        changed |= groupIntoNodeGroup.changed;

        ReadKeyBinding moveSelection = readKeyBindingWithLegacy(viewport, legacy, "moveSelection", defaults.keyBindings.viewport.moveSelection);
        config.keyBindings.viewport.moveSelection = moveSelection.value;
        changed |= moveSelection.changed;

        ReadString clearSlot = readShortcutTextWithLegacy(shopEditor, legacy, "clearSlot", "shopEditorClearSlot", defaults.keyBindings.shopEditor.clearSlot);
        config.keyBindings.shopEditor.clearSlot = clearSlot.value;
        changed |= clearSlot.changed;

        return changed;
    }

    private static boolean sanitizeKeyBindings(AppConfig.KeyBindingsConfig keyBindings, AppConfig.KeyBindingsConfig defaults) {
        boolean changed = false;

        if (keyBindings.global == null) {
            keyBindings.global = defaults.global;
            changed = true;
        } else {
            changed |= sanitizeKeyBinding(keyBindings.global.undo, defaults.global.undo, value -> keyBindings.global.undo = value);
            changed |= sanitizeKeyBinding(keyBindings.global.redo, defaults.global.redo, value -> keyBindings.global.redo = value);
            changed |= sanitizeKeyBinding(keyBindings.global.save, defaults.global.save, value -> keyBindings.global.save = value);
            changed |= sanitizeKeyBinding(keyBindings.global.copy, defaults.global.copy, value -> keyBindings.global.copy = value);
            changed |= sanitizeKeyBinding(keyBindings.global.paste, defaults.global.paste, value -> keyBindings.global.paste = value);
        }

        if (keyBindings.viewport == null) {
            keyBindings.viewport = defaults.viewport;
            changed = true;
        } else {
            changed |= sanitizeKeyBinding(keyBindings.viewport.delete, defaults.viewport.delete, value -> keyBindings.viewport.delete = value);
            changed |= sanitizeKeyBinding(keyBindings.viewport.toggleSnapToGrid, defaults.viewport.toggleSnapToGrid, value -> keyBindings.viewport.toggleSnapToGrid = value);
            changed |= sanitizeKeyBinding(keyBindings.viewport.toggleGridAndAxis, defaults.viewport.toggleGridAndAxis, value -> keyBindings.viewport.toggleGridAndAxis = value);
            changed |= sanitizeKeyBinding(keyBindings.viewport.groupIntoFrame, defaults.viewport.groupIntoFrame, value -> keyBindings.viewport.groupIntoFrame = value);
            changed |= sanitizeKeyBinding(keyBindings.viewport.groupIntoNodeGroup, defaults.viewport.groupIntoNodeGroup, value -> keyBindings.viewport.groupIntoNodeGroup = value);
            changed |= sanitizeKeyBinding(keyBindings.viewport.moveSelection, defaults.viewport.moveSelection, value -> keyBindings.viewport.moveSelection = value);
        }

        if (keyBindings.shopEditor == null) {
            keyBindings.shopEditor = defaults.shopEditor;
            changed = true;
        } else {
            changed |= sanitizeShortcutText(keyBindings.shopEditor.clearSlot, defaults.shopEditor.clearSlot, value -> keyBindings.shopEditor.clearSlot = value);
        }

        return changed;
    }

    private static JsonObject readObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static ReadString readString(JsonObject parent, String key) {
        if (!parent.has(key)) return ReadString.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadString.invalid();
        try {
            return ReadString.valid(element.getAsString(), !element.getAsJsonPrimitive().isString());
        } catch (Exception ignored) {
            return ReadString.invalid();
        }
    }

    private static ReadInt readInt(JsonObject parent, String key) {
        if (!parent.has(key)) return ReadInt.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadInt.invalid();
        try {
            boolean isString = element.getAsJsonPrimitive().isString();
            if (!element.getAsJsonPrimitive().isNumber() && !isString) return ReadInt.invalid();
            String raw = element.getAsString().trim();
            if (!isStrictInteger(raw)) return ReadInt.invalid();
            return ReadInt.valid(Integer.parseInt(raw), isString);
        } catch (Exception ignored) {
        }
        return ReadInt.invalid();
    }

    private static ReadFloat readFloat(JsonObject parent, String key) {
        if (!parent.has(key)) return ReadFloat.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadFloat.invalid();
        try {
            if (element.getAsJsonPrimitive().isNumber()) {
                return ReadFloat.valid(element.getAsFloat(), false);
            }
            if (element.getAsJsonPrimitive().isString()) {
                return ReadFloat.valid(Float.parseFloat(element.getAsString().trim()), true);
            }
        } catch (Exception ignored) {
        }
        return ReadFloat.invalid();
    }

    private static boolean isStrictInteger(String value) {
        if (value == null || value.isEmpty()) return false;
        int start = value.charAt(0) == '-' ? 1 : 0;
        if (start == value.length()) return false;
        for (int i = start; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) return false;
        }
        return true;
    }

    private static ReadBoolean readBoolean(JsonObject parent, String key) {
        if (!parent.has(key)) return ReadBoolean.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadBoolean.invalid();
        try {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return ReadBoolean.valid(element.getAsBoolean(), false);
            }
            if (element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if ("true".equalsIgnoreCase(value)) return ReadBoolean.valid(true, true);
                if ("false".equalsIgnoreCase(value)) return ReadBoolean.valid(false, true);
            }
        } catch (Exception ignored) {
        }
        return ReadBoolean.invalid();
    }

    private static ReadList readStringList(JsonObject parent, String key) {
        if (!parent.has(key)) return ReadList.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonArray()) return ReadList.invalid();

        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>();
        boolean changed = false;
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                values.add(item.getAsString());
            } else {
                changed = true;
            }
        }
        return ReadList.valid(values, changed);
    }

    private static ReadKeyBinding readKeyBinding(JsonObject parent, String key, String defaultValue) {
        ReadString value = readString(parent, key);
        if (!value.valid) return ReadKeyBinding.defaulted(defaultValue);

        KeyBinding binding = KeyBinding.parse(value.value);
        if (binding == null) return ReadKeyBinding.defaulted(defaultValue);
        return new ReadKeyBinding(binding.text, value.changed || !binding.text.equals(value.value));
    }

    private static ReadKeyBinding readKeyBindingWithLegacy(JsonObject parent, JsonObject legacy, String key, String defaultValue) {
        if (parent != null && parent.has(key)) {
            return readKeyBinding(parent, key, defaultValue);
        }
        return readKeyBinding(legacy, key, defaultValue);
    }

    private static ReadString readShortcutTextWithLegacy(JsonObject parent,
                                                         JsonObject legacy,
                                                         String key,
                                                         String legacyKey,
                                                         String defaultValue) {
        ReadString value = parent != null && parent.has(key)
                ? readString(parent, key)
                : readString(legacy, legacyKey);
        if (!value.valid || value.value == null || value.value.isBlank()) {
            return ReadString.valid(defaultValue, true);
        }

        String normalized = value.value.trim();
        return ReadString.valid(normalized, value.changed || !normalized.equals(value.value));
    }

    private static boolean sanitizeKeyBinding(String value, String defaultValue, StringConsumer setter) {
        KeyBinding binding = KeyBinding.parse(value);
        if (binding == null) {
            setter.accept(defaultValue);
            return true;
        }
        if (!binding.text.equals(value)) {
            setter.accept(binding.text);
            return true;
        }
        return false;
    }

    private static boolean sanitizeShortcutText(String value, String defaultValue, StringConsumer setter) {
        if (value == null || value.isBlank()) {
            setter.accept(defaultValue);
            return true;
        }

        String normalized = value.trim();
        if (!normalized.equals(value)) {
            setter.accept(normalized);
            return true;
        }
        return false;
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null || keys == null) return false;
        for (String key : keys) {
            if (object.has(key)) return true;
        }
        return false;
    }

    private static NormalizeList normalizeQuickAccessPaths(List<String> source) {
        List<String> normalized = new ArrayList<>();
        boolean changed = false;
        for (String path : source) {
            if (path == null || path.isBlank()) {
                changed = true;
                continue;
            }

            File file = new File(path);
            if (!file.exists() || !file.isDirectory()) {
                changed = true;
                continue;
            }

            if (normalized.contains(path)) {
                changed = true;
                continue;
            }
            normalized.add(path);
        }
        changed |= normalized.size() != source.size();
        return new NormalizeList(normalized, changed);
    }

    private static NormalizeList normalizeFavoriteGraphPaths(List<String> source) {
        List<String> normalized = new ArrayList<>();
        boolean changed = false;
        for (String path : source) {
            if (path == null || path.isBlank()) {
                changed = true;
                continue;
            }

            File file = new File(path);
            if (!file.exists() || !file.isFile() || !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".json")) {
                changed = true;
                continue;
            }

            String normalizedPath;
            try {
                normalizedPath = file.getCanonicalPath();
            } catch (Exception ignored) {
                normalizedPath = file.getAbsolutePath();
            }

            if (normalized.contains(normalizedPath)) {
                changed = true;
                continue;
            }
            normalized.add(normalizedPath);
            changed |= !normalizedPath.equals(path);
        }
        changed |= normalized.size() != source.size();
        return new NormalizeList(normalized, changed);
    }

    record Result(AppConfig config, boolean changed) {
    }

    private record NormalizeList(List<String> value, boolean changed) {
    }

    private record ReadString(boolean valid, String value, boolean changed) {
        static ReadString valid(String value, boolean changed) {
            return new ReadString(true, value, changed);
        }

        static ReadString invalid() {
            return new ReadString(false, null, true);
        }
    }

    private record ReadInt(boolean valid, int value, boolean changed) {
        static ReadInt valid(int value, boolean changed) {
            return new ReadInt(true, value, changed);
        }

        static ReadInt invalid() {
            return new ReadInt(false, 0, true);
        }
    }

    private record ReadFloat(boolean valid, float value, boolean changed) {
        static ReadFloat valid(float value, boolean changed) {
            return new ReadFloat(true, value, changed);
        }

        static ReadFloat invalid() {
            return new ReadFloat(false, 0.0f, true);
        }
    }

    private record ReadBoolean(boolean valid, boolean value, boolean changed) {
        static ReadBoolean valid(boolean value, boolean changed) {
            return new ReadBoolean(true, value, changed);
        }

        static ReadBoolean invalid() {
            return new ReadBoolean(false, false, true);
        }
    }

    private record ReadList(boolean valid, List<String> value, boolean changed) {
        static ReadList valid(List<String> value, boolean changed) {
            return new ReadList(true, value, changed);
        }

        static ReadList invalid() {
            return new ReadList(false, List.of(), true);
        }
    }

    private record ReadKeyBinding(String value, boolean changed) {
        static ReadKeyBinding defaulted(String defaultValue) {
            return new ReadKeyBinding(defaultValue, true);
        }
    }

    private interface StringConsumer {
        void accept(String value);
    }
}
