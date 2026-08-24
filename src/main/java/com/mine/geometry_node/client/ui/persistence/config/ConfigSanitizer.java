package com.mine.geometry_node.client.ui.persistence.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

final class ConfigSanitizer {
    private static final float RIGHT_SIDEBAR_WEIGHT_MIN = 0.08f;
    private static final float RIGHT_SIDEBAR_WEIGHT_MAX = 0.75f;

    private ConfigSanitizer() {
    }

    static boolean looksLikeConfig(JsonObject root) {
        return root != null && (root.has("assetBrowser") || root.has("viewport") || root.has("node")
                || root.has("networkTransfer") || root.has("previewCache") || root.has("terminal")
                || root.has("keyBindings"));
    }

    static Result fromJson(JsonObject root) {
        AppConfig defaults = AppConfig.defaults();
        AppConfig config = new AppConfig();
        boolean changed = false;

        JsonObject assetBrowser = readObject(root, "assetBrowser");
        if (assetBrowser == null) {
            config.assetBrowser = defaults.assetBrowser;
            changed = true;
        } else {
            ReadList paths = readStringList(assetBrowser, "quickAccessPaths");
            if (paths.valid) {
                config.assetBrowser.quickAccessPaths = paths.value;
                changed |= paths.changed;
            } else {
                config.assetBrowser.quickAccessPaths = defaults.assetBrowser.quickAccessPaths;
                changed = true;
            }

            ReadList favorites = readStringList(assetBrowser, "favoriteAssetPaths");
            if (favorites.valid) {
                NormalizeList normalized = normalizeFavoriteAssetPaths(favorites.value);
                config.assetBrowser.favoriteAssetPaths = normalized.value;
                changed |= favorites.changed || normalized.changed;
            } else {
                config.assetBrowser.favoriteAssetPaths = defaults.assetBrowser.favoriteAssetPaths;
                changed = true;
            }

            ReadString viewMode = readString(assetBrowser, "viewMode");
            if (viewMode.valid && BuiltinConfigEntries.ASSET_VIEW_MODE.choices().contains(viewMode.value)) {
                config.assetBrowser.viewMode = viewMode.value;
                changed |= viewMode.changed;
            } else {
                config.assetBrowser.viewMode = defaults.assetBrowser.viewMode;
                changed = true;
            }

            ReadBoolean rightSidebarVisible = readBoolean(assetBrowser, "rightSidebarVisible");
            if (rightSidebarVisible.valid) {
                config.assetBrowser.rightSidebarVisible = rightSidebarVisible.value;
                changed |= rightSidebarVisible.changed;
            } else {
                config.assetBrowser.rightSidebarVisible = defaults.assetBrowser.rightSidebarVisible;
                changed = true;
            }

            ReadFloat rightSidebarWeight = readFloat(assetBrowser, "rightSidebarWeight");
            if (rightSidebarWeight.valid && Float.isFinite(rightSidebarWeight.value)
                    && rightSidebarWeight.value >= RIGHT_SIDEBAR_WEIGHT_MIN
                    && rightSidebarWeight.value <= RIGHT_SIDEBAR_WEIGHT_MAX) {
                config.assetBrowser.rightSidebarWeight = rightSidebarWeight.value;
                changed |= rightSidebarWeight.changed;
            } else {
                config.assetBrowser.rightSidebarWeight = defaults.assetBrowser.rightSidebarWeight;
                changed = true;
            }

            ReadString rightSidebarTab = readString(assetBrowser, "rightSidebarTab");
            if (rightSidebarTab.valid && isSidebarTabId(rightSidebarTab.value)) {
                config.assetBrowser.rightSidebarTab = rightSidebarTab.value;
                changed |= rightSidebarTab.changed;
            } else {
                config.assetBrowser.rightSidebarTab = defaults.assetBrowser.rightSidebarTab;
                changed = true;
            }
        }

        JsonObject viewport = readObject(root, "viewport");
        if (viewport == null) {
            config.viewport = defaults.viewport;
            changed = true;
        } else {
            ReadInt gridSize = readInt(viewport, "gridSize");
            if (gridSize.valid && gridSize.value >= BuiltinConfigEntries.VIEWPORT_GRID_SIZE.min()
                    && gridSize.value <= BuiltinConfigEntries.VIEWPORT_GRID_SIZE.max()) {
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

            ReadBoolean rightSidebarVisible = readBoolean(viewport, "rightSidebarVisible");
            if (rightSidebarVisible.valid) {
                config.viewport.rightSidebarVisible = rightSidebarVisible.value;
                changed |= rightSidebarVisible.changed;
            } else {
                config.viewport.rightSidebarVisible = defaults.viewport.rightSidebarVisible;
                changed = true;
            }

            ReadFloat rightSidebarWeight = readFloat(viewport, "rightSidebarWeight");
            if (rightSidebarWeight.valid && Float.isFinite(rightSidebarWeight.value)
                    && rightSidebarWeight.value >= RIGHT_SIDEBAR_WEIGHT_MIN
                    && rightSidebarWeight.value <= RIGHT_SIDEBAR_WEIGHT_MAX) {
                config.viewport.rightSidebarWeight = rightSidebarWeight.value;
                changed |= rightSidebarWeight.changed;
            } else {
                config.viewport.rightSidebarWeight = defaults.viewport.rightSidebarWeight;
                changed = true;
            }

            ReadString rightSidebarTab = readString(viewport, "rightSidebarTab");
            if (rightSidebarTab.valid && isSidebarTabId(rightSidebarTab.value)) {
                config.viewport.rightSidebarTab = rightSidebarTab.value;
                changed |= rightSidebarTab.changed;
            } else {
                config.viewport.rightSidebarTab = defaults.viewport.rightSidebarTab;
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
                    && cornerRadius.value >= BuiltinConfigEntries.NODE_CORNER_RADIUS.min()
                    && cornerRadius.value <= BuiltinConfigEntries.NODE_CORNER_RADIUS.max()) {
                config.node.cornerRadius = cornerRadius.value;
                changed |= cornerRadius.changed;
            } else {
                config.node.cornerRadius = defaults.node.cornerRadius;
                changed = true;
            }
        }

        JsonObject keyBindings = readObject(root, "keyBindings");
        changed |= readKeyBindings(keyBindings, config, defaults);

        JsonObject networkTransfer = readObject(root, "networkTransfer");
        if (networkTransfer == null) {
            config.networkTransfer = defaults.networkTransfer;
            changed = true;
        } else {
            config.networkTransfer = new AppConfig.NetworkTransferConfig();
            changed |= readNetworkTransfer(networkTransfer, config.networkTransfer, defaults.networkTransfer);
        }
        JsonObject previewCache = readObject(root, "previewCache");
        if (previewCache == null) {
            config.previewCache = defaults.previewCache;
            changed = true;
        } else {
            config.previewCache = new AppConfig.PreviewCacheConfig();
            changed |= readPreviewCache(previewCache, config.previewCache, defaults.previewCache);
        }

        // P4 no longer persists CLI launch commands; rewrite legacy terminal settings away.
        if (root.has("terminal")) changed = true;

        changed |= ConfigRegistry.INSTANCE.normalize(config);
        return new Result(config, changed);
    }

    static Result sanitize(AppConfig config) {
        if (config == null) return new Result(AppConfig.defaults(), true);

        boolean changed = false;
        AppConfig defaults = AppConfig.defaults();

        if (config.assetBrowser == null) {
            config.assetBrowser = defaults.assetBrowser;
            changed = true;
        } else {
            if (config.assetBrowser.quickAccessPaths == null) {
                config.assetBrowser.quickAccessPaths = defaults.assetBrowser.quickAccessPaths;
                changed = true;
            }

            if (config.assetBrowser.favoriteAssetPaths == null) {
                config.assetBrowser.favoriteAssetPaths = defaults.assetBrowser.favoriteAssetPaths;
                changed = true;
            } else {
                NormalizeList normalized = normalizeFavoriteAssetPaths(config.assetBrowser.favoriteAssetPaths);
                if (normalized.changed) {
                    config.assetBrowser.favoriteAssetPaths = normalized.value;
                    changed = true;
                }
            }

            if (!BuiltinConfigEntries.ASSET_VIEW_MODE.choices().contains(config.assetBrowser.viewMode)) {
                config.assetBrowser.viewMode = defaults.assetBrowser.viewMode;
                changed = true;
            }
            if (!Float.isFinite(config.assetBrowser.rightSidebarWeight)
                    || config.assetBrowser.rightSidebarWeight < RIGHT_SIDEBAR_WEIGHT_MIN
                    || config.assetBrowser.rightSidebarWeight > RIGHT_SIDEBAR_WEIGHT_MAX) {
                config.assetBrowser.rightSidebarWeight = defaults.assetBrowser.rightSidebarWeight;
                changed = true;
            }
            if (!isSidebarTabId(config.assetBrowser.rightSidebarTab)) {
                config.assetBrowser.rightSidebarTab = defaults.assetBrowser.rightSidebarTab;
                changed = true;
            }
        }

        if (config.viewport == null) {
            config.viewport = defaults.viewport;
            changed = true;
        } else {
            if (config.viewport.gridSize < BuiltinConfigEntries.VIEWPORT_GRID_SIZE.min()
                    || config.viewport.gridSize > BuiltinConfigEntries.VIEWPORT_GRID_SIZE.max()) {
                config.viewport.gridSize = defaults.viewport.gridSize;
                changed = true;
            }
            if (!Float.isFinite(config.viewport.rightSidebarWeight)
                    || config.viewport.rightSidebarWeight < RIGHT_SIDEBAR_WEIGHT_MIN
                    || config.viewport.rightSidebarWeight > RIGHT_SIDEBAR_WEIGHT_MAX) {
                config.viewport.rightSidebarWeight = defaults.viewport.rightSidebarWeight;
                changed = true;
            }
            if (!isSidebarTabId(config.viewport.rightSidebarTab)) {
                config.viewport.rightSidebarTab = defaults.viewport.rightSidebarTab;
                changed = true;
            }
        }

        if (config.node == null) {
            config.node = defaults.node;
            changed = true;
        } else {
            if (!Float.isFinite(config.node.cornerRadius)
                    || config.node.cornerRadius < BuiltinConfigEntries.NODE_CORNER_RADIUS.min()
                    || config.node.cornerRadius > BuiltinConfigEntries.NODE_CORNER_RADIUS.max()) {
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

        if (config.networkTransfer == null) {
            config.networkTransfer = defaults.networkTransfer;
            changed = true;
        }
        if (config.previewCache == null) {
            config.previewCache = defaults.previewCache;
            changed = true;
        }
        changed |= ConfigRegistry.INSTANCE.normalize(config);
        return new Result(config, changed);
    }

    private static boolean readNetworkTransfer(JsonObject source, AppConfig.NetworkTransferConfig target,
                                               AppConfig.NetworkTransferConfig defaults) {
        boolean changed = false;
        ReadInt maxUpload = readInt(source, "maxUploadFileSizeMiB");
        target.maxUploadFileSizeMiB = maxUpload.valid ? maxUpload.value : defaults.maxUploadFileSizeMiB;
        changed |= !maxUpload.valid || maxUpload.changed;
        ReadInt maxDownload = readInt(source, "maxDownloadFileSizeMiB");
        target.maxDownloadFileSizeMiB = maxDownload.valid ? maxDownload.value : defaults.maxDownloadFileSizeMiB;
        changed |= !maxDownload.valid || maxDownload.changed;
        ReadInt chunkSize = readInt(source, "chunkSizeKiB");
        target.chunkSizeKiB = chunkSize.valid ? chunkSize.value : defaults.chunkSizeKiB;
        changed |= !chunkSize.valid || chunkSize.changed;
        ReadInt uploadRate = readInt(source, "uploadRateLimitKiBps");
        target.uploadRateLimitKiBps = uploadRate.valid ? uploadRate.value : defaults.uploadRateLimitKiBps;
        changed |= !uploadRate.valid || uploadRate.changed;
        ReadInt downloadRate = readInt(source, "downloadRateLimitKiBps");
        target.downloadRateLimitKiBps = downloadRate.valid ? downloadRate.value : defaults.downloadRateLimitKiBps;
        changed |= !downloadRate.valid || downloadRate.changed;
        ReadInt completedHistory = readInt(source, "completedHistoryLimit");
        target.completedHistoryLimit = completedHistory.valid ? completedHistory.value : defaults.completedHistoryLimit;
        changed |= !completedHistory.valid || completedHistory.changed;
        ReadInt failedHistory = readInt(source, "failedHistoryLimit");
        target.failedHistoryLimit = failedHistory.valid ? failedHistory.value : defaults.failedHistoryLimit;
        changed |= !failedHistory.valid || failedHistory.changed;
        return changed;
    }

    private static boolean readPreviewCache(JsonObject source, AppConfig.PreviewCacheConfig target,
                                            AppConfig.PreviewCacheConfig defaults) {
        boolean changed = false;
        ReadInt maxSize = readInt(source, "maxSizeMiB");
        target.maxSizeMiB = maxSize.valid ? maxSize.value : defaults.maxSizeMiB;
        changed |= !maxSize.valid || maxSize.changed;
        ReadString location = readString(source, "location");
        target.location = location.valid ? location.value : defaults.location;
        changed |= !location.valid || location.changed;
        return changed;
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
                "toggleSnapToGrid", "toggleGridAndAxis", "toggleRightSidebar", "groupIntoFrame",
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

        ReadKeyBinding cut = readKeyBindingWithLegacy(global, legacy, "cut", defaults.keyBindings.global.cut);
        config.keyBindings.global.cut = cut.value;
        changed |= cut.changed;

        ReadKeyBinding globalDelete = global == null
                ? ReadKeyBinding.defaulted(defaults.keyBindings.global.delete)
                : readKeyBinding(global, "delete", defaults.keyBindings.global.delete);
        config.keyBindings.global.delete = globalDelete.value;
        changed |= globalDelete.changed;

        ReadKeyBinding rename = readKeyBindingWithLegacy(global, legacy, "rename", defaults.keyBindings.global.rename);
        config.keyBindings.global.rename = rename.value;
        changed |= rename.changed;

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

        ReadKeyBinding toggleRightSidebar = readKeyBindingWithLegacy(viewport, legacy, "toggleRightSidebar", defaults.keyBindings.viewport.toggleRightSidebar);
        config.keyBindings.viewport.toggleRightSidebar = toggleRightSidebar.value;
        changed |= toggleRightSidebar.changed;

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
        }

        if (keyBindings.viewport == null) {
            keyBindings.viewport = defaults.viewport;
            changed = true;
        }

        if (keyBindings.shopEditor == null) {
            keyBindings.shopEditor = defaults.shopEditor;
            changed = true;
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

    private static boolean isSidebarTabId(String value) {
        if (value == null || value.isBlank() || value.length() > 64) return false;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') return false;
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
        if (value.value != null && value.value.isBlank()) return new ReadKeyBinding("", value.changed);

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
        if (!value.valid || value.value == null) {
            return ReadString.valid(defaultValue, true);
        }

        String normalized = value.value.trim();
        return ReadString.valid(normalized, value.changed || !normalized.equals(value.value));
    }

    private static boolean hasAny(JsonObject object, String... keys) {
        if (object == null || keys == null) return false;
        for (String key : keys) {
            if (object.has(key)) return true;
        }
        return false;
    }

    private static NormalizeList normalizeFavoriteAssetPaths(List<String> source) {
        List<String> normalized = new ArrayList<>();
        boolean changed = false;
        for (String path : source) {
            if (path == null || path.isBlank()) {
                changed = true;
                continue;
            }

            File file = new File(path);
            if (!file.exists() || !file.isFile()) {
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

}
