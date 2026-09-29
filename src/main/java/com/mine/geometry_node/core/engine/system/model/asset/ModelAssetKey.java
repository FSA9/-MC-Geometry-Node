package com.mine.geometry_node.core.engine.system.model.asset;
import java.util.Objects;
/** Cache identity: source namespace, complete manifest hash, import profile and importer revision. */
public record ModelAssetKey(String source, String logicalId, String contentHash, String profile, int importerRevision) {
    public ModelAssetKey {
        requireName(source); requireName(logicalId); requireName(profile);
        if (!Objects.requireNonNull(contentHash).matches("[0-9a-f]{64}") || importerRevision < 1)
            throw new IllegalArgumentException("invalid content hash/importer revision");
    }
    private static void requireName(String name) {
        if (name == null || !name.matches("[a-zA-Z0-9_.:/-]{1,256}") || name.contains(".."))
            throw new IllegalArgumentException("invalid asset identifier");
    }
}
