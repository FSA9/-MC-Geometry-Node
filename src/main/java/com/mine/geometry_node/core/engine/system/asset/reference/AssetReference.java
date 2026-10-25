package com.mine.geometry_node.core.engine.system.asset.reference;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A normalized, source-qualified asset reference.
 *
 * <p>Unqualified paths require a source chosen by the caller. Internal runtime
 * sources such as {@code resource:} and {@code server:} remain outside this parser.</p>
 */
public record AssetReference(AssetSource source, String location) {
    public AssetReference {
        Objects.requireNonNull(source, "source");
        if (location == null || location.isBlank()) {
            throw new IllegalArgumentException("asset location must not be empty");
        }
        location = location.trim();
    }

    public static AssetReference parse(String raw) {
        return AssetSourceRegistry.parse(raw);
    }

    public static AssetReference parse(String raw, AssetSource defaultSource) {
        return AssetSourceRegistry.parse(raw, defaultSource);
    }

    public String serialize() {
        return source.id() + ":" + location;
    }

    public boolean isLocal() {
        return source == AssetSourceRegistry.LOCAL;
    }

    public boolean isPack() {
        return source == AssetSourceRegistry.PACK;
    }

    public boolean isRemote() {
        return source == AssetSourceRegistry.REMOTE;
    }

    public boolean isWorldPack() {
        return source == AssetSourceRegistry.WORLD_PACK;
    }

    /** Resolves filesystem-backed sources using the default registry. */
    public Path resolvePath(AssetResolutionContext context) {
        return source.resolver().resolve(this, context);
    }

    static String normalizeRelative(String value, String label, boolean requirePackSegment) {
        String normalized = value.replace('\\', '/');
        if (normalized.isBlank() || normalized.startsWith("/") || isWindowsAbsolute(normalized)) {
            throw new IllegalArgumentException(label + " must be relative");
        }
        String[] segments = normalized.split("/", -1);
        if (requirePackSegment && segments.length < 2) {
            throw new IllegalArgumentException("pack reference must contain a pack id and path");
        }
        StringBuilder result = new StringBuilder(normalized.length());
        for (String segment : segments) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("invalid " + label + " path segment");
            }
            if (segment.indexOf('\0') >= 0 || segment.indexOf(':') >= 0) {
                throw new IllegalArgumentException(label + " contains an invalid character");
            }
            if (result.length() > 0) result.append('/');
            result.append(segment);
        }
        return result.toString();
    }

    static boolean isWindowsAbsolute(String value) {
        return value.length() >= 3
                && Character.isLetter(value.charAt(0))
                && value.charAt(1) == ':'
                && (value.charAt(2) == '/' || value.charAt(2) == '\\');
    }

    static boolean isUncAbsolute(String value) {
        return value.startsWith("//") || value.startsWith("\\\\");
    }

}
