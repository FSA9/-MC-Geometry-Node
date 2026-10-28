package com.mine.geometry_node.core.engine.system.asset;

import java.util.Collection;
import java.util.Objects;

/** Repository address: legacy Common IDs remain unqualified; world packs use worldpack:. */
public record AssetRepositoryPath(Root root, String relativePath) {
    private static final String WORLD_PACK_PREFIX = "worldpack:";

    public enum Root { COMMON, WORLD_PACK }

    public AssetRepositoryPath {
        Objects.requireNonNull(root, "root");
        relativePath = AssetRelativePath.normalize(relativePath, true);
        if (relativePath.indexOf(':') >= 0) throw new IllegalArgumentException("invalid repository path qualifier");
    }

    public static AssetRepositoryPath parse(String path, boolean allowRoot) {
        Objects.requireNonNull(path, "path");
        String value = path.trim();
        Root root = Root.COMMON;
        if (value.startsWith(WORLD_PACK_PREFIX)) {
            root = Root.WORLD_PACK;
            value = value.substring(WORLD_PACK_PREFIX.length());
        }
        AssetRepositoryPath result = new AssetRepositoryPath(root, value);
        if (!allowRoot && result.isRoot()) throw new IllegalArgumentException("repository root is not an asset path");
        return result;
    }

    public String serialize() {
        return (isCommon() ? "" : WORLD_PACK_PREFIX) + relativePath;
    }

    public boolean isRoot() { return relativePath.isEmpty(); }
    public boolean isCommon() { return root == Root.COMMON; }

    public AssetRepositoryPath parent() {
        int separator = relativePath.lastIndexOf('/');
        return new AssetRepositoryPath(root, separator < 0 ? "" : relativePath.substring(0, separator));
    }

    /** Resolves a relative descendant without allowing a qualifier to switch repositories. */
    public AssetRepositoryPath resolve(String child) {
        String normalized = AssetRelativePath.normalize(child, false);
        return new AssetRepositoryPath(root, isRoot() ? normalized : relativePath + "/" + normalized);
    }

    public String name() {
        int separator = relativePath.lastIndexOf('/');
        return relativePath.substring(separator + 1);
    }

    /** Validates a whole operation before any filesystem side effects. */
    public static Root requireSameRoot(Collection<String> paths, String destination, boolean allowRoots) {
        Root selected = destination == null ? null : parse(destination, true).root();
        for (String path : paths) {
            Root candidate = parse(path, allowRoots).root();
            if (selected != null && selected != candidate) {
                throw new IllegalArgumentException("operation cannot mix Common and Packs roots");
            }
            selected = candidate;
        }
        return selected;
    }
}
