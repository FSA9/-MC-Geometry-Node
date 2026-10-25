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

    public static final AssetSource LOCAL = registerBuiltin("local", (reference, gameDirectory) ->
            Path.of(reference.location()).toAbsolutePath().normalize());
    public static final AssetSource PACK = registerBuiltin("pack", (reference, gameDirectory) -> {
        Objects.requireNonNull(gameDirectory, "gameDirectory");
        Path root = gameDirectory.toAbsolutePath().normalize()
                .resolve("geometry_nodes").resolve("packs").normalize();
        Path candidate = root.resolve(reference.location().replace('/', java.io.File.separatorChar)).normalize();
        if (!candidate.startsWith(root) || !isContainedRealPath(root, candidate)) {
            throw new IllegalArgumentException("pack reference escapes the pack root");
        }
        return candidate;
    });
    public static final AssetSource REMOTE = registerBuiltin("remote", (reference, gameDirectory) -> null);

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
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("asset reference must not be empty");
        String value = raw.trim();
        if (value.length() > 4096) throw new IllegalArgumentException("asset reference is too long");

        // Check absolute paths before looking for a scheme so Windows drive letters
        // (for example C:/image.png) are never mistaken for a source id.
        if (isAbsolute(value)) {
            return new AssetReference(LOCAL, value);
        }

        int separator = value.indexOf(':');
        if (separator > 0 && isScheme(value.substring(0, separator))) {
            String id = normalizeId(value.substring(0, separator));
            AssetSource source = find(id);
            if (source == null) throw new IllegalArgumentException("unknown asset source: " + id);
            String location = value.substring(separator + 1);
            if (source == PACK) location = AssetReference.normalizeRelative(location, "pack", true);
            else if (source == REMOTE) {
                location = location.replace('\\', '/');
                while (location.startsWith("/")) location = location.substring(1);
                location = AssetReference.normalizeRelative(location, "remote", false);
            }
            else if (source == LOCAL && !isAbsolute(location)) throw new IllegalArgumentException("local reference must be absolute");
            return new AssetReference(source, location);
        }

        return new AssetReference(PACK, AssetReference.normalizeRelative(value, "pack", true));
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
