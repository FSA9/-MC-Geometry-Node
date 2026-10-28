package com.mine.geometry_node.client.feature.asset.navigation;

import com.mine.geometry_node.client.config.ConfigManager;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.path.AssetBrowserPathPolicy;
import com.mine.geometry_node.client.feature.asset.repository.AssetLocation;
import com.mine.geometry_node.client.platform.ClientServerIdentity;
import com.mine.geometry_node.core.engine.system.asset.AssetRepositoryPath;
import net.minecraft.client.Minecraft;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** Persistent directory shortcuts; remote entries are isolated by connected server/world. */
public final class AssetQuickAccessStore {
    public record Entry(String key, AssetLocation location, String label) {}

    private final ConfigManager mConfigManager;
    private final Supplier<String> mServerIdentity;

    public AssetQuickAccessStore() {
        this(ConfigManager.INSTANCE, AssetQuickAccessStore::serverIdentity);
    }

    public AssetQuickAccessStore(ConfigManager configManager, Supplier<String> serverIdentity) {
        mConfigManager = Objects.requireNonNull(configManager, "configManager");
        mServerIdentity = Objects.requireNonNull(serverIdentity, "serverIdentity");
    }

    public List<Entry> entries(AssetSourceKind source) {
        String server = source == AssetSourceKind.REMOTE ? mServerIdentity.get() : null;
        if (source == AssetSourceKind.REMOTE && server == null) return List.of();
        var config = mConfigManager.getConfig().assetBrowser;
        List<Entry> result = new ArrayList<>();
        if (source == AssetSourceKind.LOCAL) {
            for (String path : config.quickAccessPaths) {
                File directory = AssetBrowserPathPolicy.resolveConfigPath(path);
                if (directory != null && directory.isDirectory()) {
                    result.add(new Entry(path, new AssetLocation.Local(directory, false, List.of()),
                            directory.getName().isEmpty() ? directory.getPath() : directory.getName()));
                }
            }
        } else {
            for (String path : config.remoteQuickAccessPaths.getOrDefault(server, List.of())) {
                AssetRepositoryPath address = AssetRepositoryPath.parse(path, true);
                result.add(new Entry(remoteKey(server, path), new AssetLocation.Remote(address.serialize(), false),
                        address.isRoot() ? (address.isCommon() ? "Common" : "Packs") : address.name()));
            }
        }
        return List.copyOf(result);
    }

    public boolean add(AssetLocation location) {
        if (location instanceof AssetLocation.Local local) {
            if (local.favorites() || local.directory() == null) return false;
            String path = AssetBrowserPathPolicy.toConfigPath(local.directory());
            if (!AssetBrowserPathPolicy.canPersistQuickAccessPath(path)) return false;
            if (entries(AssetSourceKind.LOCAL).stream().anyMatch(entry ->
                    entry.location() instanceof AssetLocation.Local existing
                            && sameDirectory(existing.directory(), local.directory()))) return false;
            mConfigManager.update(config -> config.assetBrowser.quickAccessPaths.add(path));
            return true;
        }
        if (location instanceof AssetLocation.Remote remote) {
            String server = mServerIdentity.get();
            AssetRepositoryPath path = AssetRepositoryPath.parse(remote.directory(), true);
            if (server == null || path.isRoot()) return false;
            if (mConfigManager.getConfig().assetBrowser.remoteQuickAccessPaths
                    .getOrDefault(server, List.of()).contains(path.serialize())) return false;
            mConfigManager.update(config -> config.assetBrowser.remoteQuickAccessPaths
                    .computeIfAbsent(server, ignored -> new ArrayList<>()).add(path.serialize()));
            return true;
        }
        return false;
    }

    public void remove(AssetSourceKind source, String key) {
        if (source == AssetSourceKind.LOCAL) {
            mConfigManager.update(config -> config.assetBrowser.quickAccessPaths.remove(key));
            return;
        }
        String server = mServerIdentity.get();
        String path = remotePath(server, key);
        if (path == null) return;
        mConfigManager.update(config -> {
            List<String> paths = config.assetBrowser.remoteQuickAccessPaths.get(server);
            if (paths != null) paths.remove(path);
        });
    }

    /** Insertion index is relative to shortcuts in this source, never fixed/sidebar rows. */
    public void move(AssetSourceKind source, String key, int insertionIndex) {
        String server = source == AssetSourceKind.REMOTE ? mServerIdentity.get() : null;
        String path = source == AssetSourceKind.LOCAL ? key : remotePath(server, key);
        if (path == null) return;
        mConfigManager.update(config -> {
            List<String> paths = source == AssetSourceKind.LOCAL ? config.assetBrowser.quickAccessPaths
                    : config.assetBrowser.remoteQuickAccessPaths.get(server);
            if (paths == null) return;
            int from = paths.indexOf(path);
            if (from < 0) return;
            int to = Math.max(0, Math.min(paths.size(), insertionIndex));
            if (to > from) to--;
            if (to == from) return;
            paths.remove(from);
            paths.add(to, path);
        });
    }

    private static boolean sameDirectory(File first, File second) {
        return com.mine.geometry_node.client.platform.ClientPaths.sameFile(first, second);
    }

    private static String serverIdentity() {
        if (Minecraft.getInstance() == null) return null;
        try { return ClientServerIdentity.current(); }
        catch (IllegalStateException unavailable) { return null; }
    }

    // UI keys are opaque and scoped to the server that produced the row. A delayed
    // remove/reorder after a connection change must never mutate the new server.
    // Only the canonical path is persisted, inside its server's configuration map.
    private static String remoteKey(String server, String path) {
        return server + '\0' + path;
    }

    private static String remotePath(String server, String key) {
        if (server == null || key == null) return null;
        String prefix = server + '\0';
        return key.startsWith(prefix) ? key.substring(prefix.length()) : null;
    }
}
