package com.mine.geometry_node.client.model.render.compat.entity;

/** Classifies compatibility texture failures without depending on host exception types. */
final class TextureProjectionFailure extends RuntimeException {
    enum Kind { ASSET, RUNTIME }

    private final Kind kind;

    private TextureProjectionFailure(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    static TextureProjectionFailure asset(String message, Throwable cause) {
        return new TextureProjectionFailure(Kind.ASSET, message, cause);
    }

    static TextureProjectionFailure runtime(String message, Throwable cause) {
        return new TextureProjectionFailure(Kind.RUNTIME, message, cause);
    }

    Kind kind() {
        return kind;
    }

    boolean cacheForAssetLifetime() {
        return kind == Kind.ASSET;
    }
}
