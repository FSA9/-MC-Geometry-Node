package com.mine.geometry_node.core.engine.system.asset;

import java.nio.file.Path;

/** Content-aware recognition boundary for one transferable asset type. */
public interface AssetTypeRecognizer {
    /** Returns whether the logical path is a possible input for this recognizer. */
    boolean supportsCandidatePath(String normalizedPath);

    /** Returns the variant id, an empty variant, or {@code null} when the file is not this asset type. */
    String inspectVariant(Path file, String normalizedPath);
}
