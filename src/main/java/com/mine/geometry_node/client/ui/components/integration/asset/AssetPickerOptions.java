package com.mine.geometry_node.client.ui.components.integration.asset;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.repository.AssetLocation;

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/** Configuration for one single-selection asset picker. Null directory uses the source default. */
public record AssetPickerOptions(Sources sources, Target target, Predicate<AssetEntry> fileFilter,
                                 AssetLocation initialDirectory) {
    public enum Sources {
        LOCAL, REMOTE, BOTH;

        public Set<AssetSourceKind> allowedSources() {
            return switch (this) {
                case LOCAL -> Set.of(AssetSourceKind.LOCAL);
                case REMOTE -> Set.of(AssetSourceKind.REMOTE);
                case BOTH -> Set.of(AssetSourceKind.LOCAL, AssetSourceKind.REMOTE);
            };
        }

        public boolean allows(AssetSourceKind source) {
            return source != null && allowedSources().contains(source);
        }
    }

    public enum Target {
        FILE, FOLDER, FILE_OR_FOLDER;

        public boolean allowsFiles() { return this != FOLDER; }
        public boolean allowsFolders() { return this != FILE; }
    }

    public AssetPickerOptions {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(target, "target");
        fileFilter = fileFilter == null ? entry -> true : fileFilter;
        if (initialDirectory != null && !sources.allows(initialDirectory.sourceKind())) {
            throw new IllegalArgumentException("Initial directory source is not allowed");
        }
        if (initialDirectory instanceof AssetLocation.Local local
                && (local.directory() == null || local.favorites())) {
            throw new IllegalArgumentException("Initial directory must be a concrete local directory");
        }
    }

    /** Directories stay visible for navigation, regardless of the file predicate or selection target. */
    public boolean isVisible(AssetEntry entry) {
        return entry != null && sources.allows(entry.sourceKind())
                && (entry.isDirectory() || target.allowsFiles() && fileFilter.test(entry));
    }

    /** Rechecked at confirmation, including browser context-menu and double-click callbacks. */
    public boolean canSelect(AssetEntry entry) {
        if (entry == null || !sources.allows(entry.sourceKind())) return false;
        if (entry.sourceKind() == AssetSourceKind.LOCAL && entry.localFile() == null) return false;
        return entry.isDirectory() ? target.allowsFolders()
                : target.allowsFiles() && fileFilter.test(entry);
    }
}
