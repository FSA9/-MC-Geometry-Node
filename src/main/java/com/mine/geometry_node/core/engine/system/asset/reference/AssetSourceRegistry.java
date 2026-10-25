package com.mine.geometry_node.core.engine.system.asset.reference;

import java.nio.file.Path;
import java.nio.file.Files;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Global registry and parser for source-qualified asset references. */
public final class AssetSourceRegistry {
    private static final Map<String, AssetSource> SOURCES = new ConcurrentHashMap<>();

    public static final AssetSource LOCAL = registerBuiltin("local", (reference, context) ->
            Path.of(reference.location()).toAbsolutePath().normalize());
    public static final AssetSource PACK = registerBuiltin("pack", (reference, context) ->
            resolvePack(reference, context.gameDirectory()));
    public static final AssetSource WORLD_PACK = registerBuiltin("worldpack", (reference, context) ->
            resolvePack(reference, context.requireWorldDirectory()));
    public static final AssetSource REMOTE = registerBuiltin("remote", (reference, context) -> null);

    private AssetSourceRegistry() {
    }

    public static AssetSource register(AssetSource source) {
        Objects.requireNonNull(source, "source");
        String id = normalizeId(source.id());
        if (!id.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException("invalid asset source id: " + source.id());
        }
        AssetSource previous = SOURCES.putIfAbsent(id, source);
        if (previous != null && previous != source) {
            throw new IllegalArgumentException("asset source already registered: " + id);
        }
        return source;
    }

    public static AssetSource find(String id) {
        return SOURCES.get(normalizeId(id));
    }

    public static AssetReference parse(String raw) {
        return parse(raw, null);
    }

    public static AssetReference parse(String raw, AssetSource defaultSource) {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("asset reference must not be empty");
        String value = raw.trim();
        if (value.length() > 4096) throw new IllegalArgumentException("asset reference is too long");

        int separator = value.indexOf(':');
        if (!isAbsolute(value) && separator > 0 && isScheme(value.substring(0, separator))) {
            String id = normalizeId(value.substring(0, separator));
            AssetSource source = find(id);
            if (source == null) throw new IllegalArgumentException("unknown asset source: " + id);
            return reference(source, value.substring(separator + 1));
        }
        if (defaultSource == null) throw new IllegalArgumentException("asset reference requires an explicit source");
        return reference(defaultSource, value);
    }

    private static AssetReference reference(AssetSource source, String location) {
        if (source == PACK || source == WORLD_PACK) {
            location = AssetReference.normalizeRelative(location, source.id(), true);
        } else if (source == REMOTE) {
            location = AssetReference.normalizeRelative(location, "remote", false);
        } else if (source == LOCAL && !isAbsolute(location)) {
            throw new IllegalArgumentException("local reference must be absolute");
        }
        return new AssetReference(source, location);
    }

    private static Path resolvePack(AssetReference reference, Path baseDirectory) {
        Path root = baseDirectory.toAbsolutePath().normalize().resolve("geometry_nodes").resolve("packs");
        Path candidate = root.resolve(AssetReference.normalizeRelative(reference.location(), reference.source().id(), true)).normalize();
        if (!candidate.startsWith(root) || !isContainedRealPath(root, candidate)) {
            throw new IllegalArgumentException("pack reference escapes the pack root");
        }
        return candidate;
    }

    private static boolean isScheme(String value) {
        return value.matches("[A-Za-z][A-Za-z0-9_-]*");
    }

    private static boolean isAbsolute(String value) {
        if (AssetReference.isWindowsAbsolute(value) || AssetReference.isUncAbsolute(value)) return true;
        try {
            return Path.of(value).isAbsolute();
        } catch (java.nio.file.InvalidPathException exception) {
            return false;
        }
    }

    private static String normalizeId(String value) {
        return Objects.requireNonNull(value, "source id").trim().toLowerCase(Locale.ROOT);
    }

    private static AssetSource registerBuiltin(String id, AssetResolver resolver) {
        AssetSource source = new BuiltinSource(id, resolver);
        SOURCES.put(id, source);
        return source;
    }

    private static boolean isContainedRealPath(Path root, Path candidate) {
        try {
            if (!Files.exists(candidate)) return true;
            Path realRoot = Files.exists(root) ? root.toRealPath() : root;
            return candidate.toRealPath().startsWith(realRoot);
        } catch (IOException exception) {
            return false;
        }
    }

    private record BuiltinSource(String id, AssetResolver resolver) implements AssetSource {
    }
}
