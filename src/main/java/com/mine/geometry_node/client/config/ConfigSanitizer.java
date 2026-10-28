package com.mine.geometry_node.client.config;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

/** Reads registered settings through their declared editor type and validation contract. */
final class ConfigSanitizer {
    private static final Gson GSON = new Gson();
    private static final float RIGHT_SIDEBAR_WEIGHT_MIN = 0.08f;
    private static final float RIGHT_SIDEBAR_WEIGHT_MAX = 0.75f;

    private ConfigSanitizer() {}

    static boolean looksLikeConfig(JsonObject root) {
        return root != null && (root.has("terminal") || ConfigRegistry.INSTANCE.entries().stream()
                .anyMatch(entry -> root.has(entry.id().split("\\.", 2)[0])));
    }

    static Result fromJson(JsonObject root) {
        if (root == null) return new Result(AppConfig.defaults(), true);
        AppConfig config = AppConfig.defaults();
        // Registry IDs identify the same JSON path used by AppConfig's persisted schema.
        // Adding a registered setting must not require another hand-maintained loader branch.
        for (ConfigEntry<?> entry : ConfigRegistry.INSTANCE.entries()) readEntry(root, config, entry);

        // The old default occupied the new editor chord. Preserve deliberate custom navigation bindings.
        JsonObject gameKeys = readObject(readObject(root, "keyBindings"), "gameView");
        if ((gameKeys == null || !gameKeys.has("edit"))
                && "CTRL+RIGHT_CLICK".equals(config.keyBindings.gameView.entityMove))
            config.keyBindings.gameView.entityMove = "SHIFT+RIGHT_CLICK";

        JsonObject assets = readObject(root, "assetBrowser");
        ReadList favorites = readStringList(assets, "favoriteAssetPaths");
        if (favorites.valid) config.assetBrowser.favoriteAssetPaths = favorites.value;
        JsonObject remoteShortcuts = readObject(assets, "remoteQuickAccessPaths");
        if (remoteShortcuts != null) {
            for (String server : remoteShortcuts.keySet()) {
                ReadList paths = readStringList(remoteShortcuts, server);
                if (paths.valid) config.assetBrowser.remoteQuickAccessPaths.put(server, paths.value);
            }
        }
        ReadBoolean assetVisible = readBoolean(assets, "rightSidebarVisible");
        if (assetVisible.valid) config.assetBrowser.rightSidebarVisible = assetVisible.value;
        ReadFloat assetWeight = readFloat(assets, "rightSidebarWeight");
        if (assetWeight.valid) config.assetBrowser.rightSidebarWeight = assetWeight.value;
        ReadString assetTab = readString(assets, "rightSidebarTab");
        if (assetTab.valid) config.assetBrowser.rightSidebarTab = assetTab.value;

        JsonObject viewport = readObject(root, "viewport");
        ReadBoolean viewportVisible = readBoolean(viewport, "rightSidebarVisible");
        if (viewportVisible.valid) config.viewport.rightSidebarVisible = viewportVisible.value;
        ReadFloat viewportWeight = readFloat(viewport, "rightSidebarWeight");
        if (viewportWeight.valid) config.viewport.rightSidebarWeight = viewportWeight.value;
        ReadString viewportTab = readString(viewport, "rightSidebarTab");
        if (viewportTab.valid) config.viewport.rightSidebarTab = viewportTab.value;

        sanitize(config);
        // Rewrite coercions, missing fields and removed settings into one canonical schema.
        return new Result(config, !root.equals(GSON.toJsonTree(config)));
    }

    @SuppressWarnings("unchecked")
    private static <T> void readEntry(JsonObject root, AppConfig config, ConfigEntry<T> entry) {
        String[] path = entry.id().split("\\.");
        JsonObject parent = root;
        for (int i = 0; i < path.length - 1; i++) parent = readObject(parent, path[i]);
        String key = path[path.length - 1];
        if (entry.keyScope() != null && useLegacyShortcut(parent, key, entry)) {
            JsonElement legacy = legacyShortcut(readObject(root, "keyBindings"), entry, key);
            if (legacy != null) {
                parent = new JsonObject();
                parent.add(key, legacy);
            }
        }
        Object value = switch (entry.editorType()) {
            case BOOLEAN -> {
                ReadBoolean read = readBoolean(parent, key);
                yield read.valid ? read.value : null;
            }
            case INTEGER -> {
                ReadInt read = readInt(parent, key);
                yield read.valid ? read.value : null;
            }
            case FLOAT -> {
                ReadFloat read = readFloat(parent, key);
                yield read.valid ? read.value : null;
            }
            case PATH_LIST -> {
                ReadList read = readStringList(parent, key);
                yield read.valid ? read.value : null;
            }
            case CHOICE, PATH, KEY_BINDING, SHORTCUT -> {
                ReadString read = readString(parent, key);
                yield read.valid ? read.value : null;
            }
        };
        // The candidate already contains defaults. Missing or mistyped JSON must not
        // be passed as null to entry normalizers that only accept concrete values.
        if (value != null) entry.set(config, (T) value);
    }

    private static boolean useLegacyShortcut(JsonObject parent, String key, ConfigEntry<?> entry) {
        if (parent == null || !parent.has(key)) return true;
        if (!entry.category().isWithin(BuiltinConfigEntries.SHORTCUT_GLOBAL)) return false;
        ReadString current = readString(parent, key);
        String value = current.valid ? validLegacyBinding(entry, current.value) : null;
        return value == null || Objects.equals(value, entry.defaultValue());
    }

    /** Preserve valid customizations when the shared binding is absent or still its old default. */
    private static JsonElement legacyShortcut(JsonObject bindings, ConfigEntry<?> entry, String key) {
        if (bindings == null) return null;
        if (entry.category().isWithin(BuiltinConfigEntries.SHORTCUT_GLOBAL)) {
            if (key.equals("delete") || key.equals("selectAll")) {
                JsonElement value = legacyCommon(readObject(bindings, "viewport"), key, entry);
                if (value != null) return value;
            }
            JsonElement value = legacyCommon(readObject(bindings, "gameView"), key, entry);
            if (value != null) return value;
            return legacyCommon(bindings, key, entry);
        }
        if (entry.category().isWithin(BuiltinConfigEntries.SHORTCUT_VIEWPORT)) return legacyCommon(bindings, key, entry);
        if (entry == BuiltinConfigEntries.SHOP_CLEAR_SLOT) return legacyCommon(bindings, "shopEditorClearSlot", entry);
        return null;
    }

    private static JsonElement legacyCommon(JsonObject bindings, String key, ConfigEntry<?> entry) {
        if (bindings == null) return null;
        ReadString read = readString(bindings, key);
        if (!read.valid) return null;
        String candidate = validLegacyBinding(entry, read.value);
        if (candidate == null) return null;
        // A was the old context default. The new shared default is Ctrl+A;
        // preserve explicitly customized selections while upgrading that default.
        if (key.equals("selectAll") && "A".equals(candidate)) return null;
        if (Objects.equals(candidate, entry.defaultValue())) return null;
        return new com.google.gson.JsonPrimitive(candidate);
    }

    @SuppressWarnings("unchecked")
    private static String validLegacyBinding(ConfigEntry<?> entry, String value) {
        ConfigEntry<String> shortcut = (ConfigEntry<String>) entry;
        return shortcut.accepts(value) ? shortcut.normalize(value) : null;
    }

    static Result sanitize(AppConfig config) {
        if (config == null) return new Result(AppConfig.defaults(), true);
        AppConfig defaults = AppConfig.defaults();
        boolean changed = false;
        if (config.assetBrowser == null) { config.assetBrowser = defaults.assetBrowser; changed = true; }
        if (config.viewport == null) { config.viewport = defaults.viewport; changed = true; }
        if (config.gameView == null) { config.gameView = defaults.gameView; changed = true; }
        if (config.node == null) { config.node = defaults.node; changed = true; }
        if (config.keyBindings == null) { config.keyBindings = defaults.keyBindings; changed = true; }
        if (config.keyBindings.global == null) { config.keyBindings.global = defaults.keyBindings.global; changed = true; }
        if (config.keyBindings.viewport == null) { config.keyBindings.viewport = defaults.keyBindings.viewport; changed = true; }
        if (config.keyBindings.gameView == null) { config.keyBindings.gameView = defaults.keyBindings.gameView; changed = true; }
        if (config.keyBindings.shopEditor == null) { config.keyBindings.shopEditor = defaults.keyBindings.shopEditor; changed = true; }

        if (config.assetBrowser.favoriteAssetPaths == null) {
            config.assetBrowser.favoriteAssetPaths = defaults.assetBrowser.favoriteAssetPaths;
            changed = true;
        } else {
            NormalizeList favorites = normalizeFavoriteAssetPaths(config.assetBrowser.favoriteAssetPaths);
            config.assetBrowser.favoriteAssetPaths = favorites.value;
            changed |= favorites.changed;
        }
        Map<String, List<String>> remoteShortcuts = normalizeRemoteQuickAccess(config.assetBrowser.remoteQuickAccessPaths);
        changed |= !Objects.equals(remoteShortcuts, config.assetBrowser.remoteQuickAccessPaths);
        config.assetBrowser.remoteQuickAccessPaths = remoteShortcuts;
        if (!validSidebarWeight(config.assetBrowser.rightSidebarWeight)) {
            config.assetBrowser.rightSidebarWeight = defaults.assetBrowser.rightSidebarWeight;
            changed = true;
        }
        if (!validSidebarWeight(config.viewport.rightSidebarWeight)) {
            config.viewport.rightSidebarWeight = defaults.viewport.rightSidebarWeight;
            changed = true;
        }
        if (!isSidebarTabId(config.assetBrowser.rightSidebarTab)) {
            config.assetBrowser.rightSidebarTab = defaults.assetBrowser.rightSidebarTab;
            changed = true;
        }
        if (!isSidebarTabId(config.viewport.rightSidebarTab)) {
            config.viewport.rightSidebarTab = defaults.viewport.rightSidebarTab;
            changed = true;
        }
        changed |= ConfigRegistry.INSTANCE.normalize(config);
        return new Result(config, changed);
    }

    private static boolean validSidebarWeight(float weight) {
        return Float.isFinite(weight) && weight >= RIGHT_SIDEBAR_WEIGHT_MIN && weight <= RIGHT_SIDEBAR_WEIGHT_MAX;
    }

    private static Map<String, List<String>> normalizeRemoteQuickAccess(Map<String, List<String>> source) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (source == null) return result;
        source.forEach((server, paths) -> {
            if (server == null || server.isBlank() || paths == null) return;
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String path : paths) {
                try {
                    AssetRepositoryPath address = AssetRepositoryPath.parse(path, false);
                    normalized.add(address.serialize());
                } catch (IllegalArgumentException | NullPointerException invalid) {
                    // Invalid user configuration must not affect other server namespaces.
                }
            }
            if (!normalized.isEmpty()) result.put(server, new ArrayList<>(normalized));
        });
        return result;
    }

    private static JsonObject readObject(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return null;
        JsonElement element = parent.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static ReadString readString(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return ReadString.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadString.invalid();
        try {
            return ReadString.valid(element.getAsString());
        } catch (Exception ignored) {
            return ReadString.invalid();
        }
    }

    private static ReadInt readInt(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return ReadInt.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadInt.invalid();
        try {
            boolean isString = element.getAsJsonPrimitive().isString();
            if (!element.getAsJsonPrimitive().isNumber() && !isString) return ReadInt.invalid();
            String raw = element.getAsString().trim();
            if (!isStrictInteger(raw)) return ReadInt.invalid();
            return ReadInt.valid(Integer.parseInt(raw));
        } catch (Exception ignored) {
        }
        return ReadInt.invalid();
    }

    private static ReadFloat readFloat(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return ReadFloat.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadFloat.invalid();
        try {
            if (element.getAsJsonPrimitive().isNumber()) {
                return ReadFloat.valid(element.getAsFloat());
            }
            if (element.getAsJsonPrimitive().isString()) {
                return ReadFloat.valid(Float.parseFloat(element.getAsString().trim()));
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
        if (parent == null || !parent.has(key)) return ReadBoolean.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonPrimitive()) return ReadBoolean.invalid();
        try {
            if (element.getAsJsonPrimitive().isBoolean()) {
                return ReadBoolean.valid(element.getAsBoolean());
            }
            if (element.getAsJsonPrimitive().isString()) {
                String value = element.getAsString().trim();
                if ("true".equalsIgnoreCase(value)) return ReadBoolean.valid(true);
                if ("false".equalsIgnoreCase(value)) return ReadBoolean.valid(false);
            }
        } catch (Exception ignored) {
        }
        return ReadBoolean.invalid();
    }

    private static ReadList readStringList(JsonObject parent, String key) {
        if (parent == null || !parent.has(key)) return ReadList.invalid();
        JsonElement element = parent.get(key);
        if (element == null || !element.isJsonArray()) return ReadList.invalid();

        JsonArray array = element.getAsJsonArray();
        List<String> values = new ArrayList<>();
        for (JsonElement item : array) {
            if (item != null && item.isJsonPrimitive() && item.getAsJsonPrimitive().isString()) {
                values.add(item.getAsString());
            }
        }
        return ReadList.valid(values);
    }

    private static NormalizeList normalizeFavoriteAssetPaths(List<String> source) {
        List<String> normalized = new ArrayList<>();
        boolean changed = false;
        for (String path : source) {
            if (path == null || path.isBlank()) {
                changed = true;
                continue;
            }

            File file;
            try {
                file = AssetBrowserPathPolicy.resolveConfigPath(path);
            } catch (java.io.UncheckedIOException migrationFailure) {
                // A conflicting migration must not erase existing favorites during config repair.
                file = new File(path);
            }
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

    private record ReadString(boolean valid, String value) {
        static ReadString valid(String value) {
            return new ReadString(true, value);
        }

        static ReadString invalid() {
            return new ReadString(false, null);
        }
    }

    private record ReadInt(boolean valid, int value) {
        static ReadInt valid(int value) {
            return new ReadInt(true, value);
        }

        static ReadInt invalid() {
            return new ReadInt(false, 0);
        }
    }

    private record ReadFloat(boolean valid, float value) {
        static ReadFloat valid(float value) {
            return new ReadFloat(true, value);
        }

        static ReadFloat invalid() {
            return new ReadFloat(false, 0.0f);
        }
    }

    private record ReadBoolean(boolean valid, boolean value) {
        static ReadBoolean valid(boolean value) {
            return new ReadBoolean(true, value);
        }

        static ReadBoolean invalid() {
            return new ReadBoolean(false, false);
        }
    }

    private record ReadList(boolean valid, List<String> value) {
        static ReadList valid(List<String> value) {
            return new ReadList(true, value);
        }

        static ReadList invalid() {
            return new ReadList(false, List.of());
        }
    }

}
