package com.mine.geometry_node.core.engine.graph.compile;

/** Stable identity and limits attached to one graph compilation request. */
public record GraphCompileContext(String assetId) {
    public static final GraphCompileContext ANONYMOUS = new GraphCompileContext("");

    public GraphCompileContext {
        assetId = assetId != null ? assetId.trim() : "";
    }

    public String diagnosticAssetId() {
        return assetId.isEmpty() ? "<anonymous>" : assetId;
    }
}
