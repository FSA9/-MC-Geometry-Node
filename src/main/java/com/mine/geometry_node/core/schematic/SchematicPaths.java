package com.mine.geometry_node.core.schematic;

import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SchematicPaths {
    private SchematicPaths() {
    }

    public static Path resolveServerPath(MinecraftServer server, String rawPath) throws IOException {
        if (server == null) {
            throw new IOException("Missing server");
        }
        String value = rawPath == null ? "" : rawPath.trim();
        if (value.isEmpty()) {
            throw new IOException("Schematic path is empty");
        }
        if (value.indexOf('\0') >= 0) {
            throw new IOException("Invalid schematic path");
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".schem") && !lower.endsWith(".schematic")) {
            throw new IOException("Schematic path must end with .schem or .schematic");
        }

        List<Path> candidates = resolveCandidates(server, value);
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }

        throw new IOException("Schematic file does not exist: " + candidates);
    }

    private static List<Path> resolveCandidates(MinecraftServer server, String value) throws IOException {
        List<Path> candidates = new ArrayList<>();

        Path raw = Path.of(value);
        if (raw.isAbsolute()) {
            candidates.add(raw.normalize());
            return candidates;
        }

        Path mountedWindowsPath = mountedWindowsPath(value);
        if (mountedWindowsPath != null) {
            candidates.add(mountedWindowsPath);
            return candidates;
        }

        String relative = value.replace('\\', '/');
        Path worldRoot = server.getWorldPath(DynamicGraphManager.GRAPH_DIR).toAbsolutePath().normalize();
        Path worldCandidate = worldRoot.resolve(relative).normalize();
        if (!worldCandidate.startsWith(worldRoot)) {
            throw new IOException("Schematic path escapes geometry_nodes");
        }
        candidates.add(worldCandidate);

        Path serverRoot = server.getServerDirectory().toAbsolutePath().normalize();
        Path sharedRoot = serverRoot.resolve("geometry_nodes").normalize();
        Path sharedCandidate = sharedRoot.resolve(relative).normalize();
        if (!sharedCandidate.startsWith(sharedRoot)) {
            throw new IOException("Schematic path escapes server geometry_nodes");
        }
        if (!sharedCandidate.equals(worldCandidate)) {
            candidates.add(sharedCandidate);
        }

        Path serverCandidate = serverRoot.resolve(relative).normalize();
        if (relative.startsWith("geometry_nodes/") && serverCandidate.startsWith(serverRoot) && !serverCandidate.equals(sharedCandidate)) {
            candidates.add(serverCandidate);
        }
        return candidates;
    }

    private static Path mountedWindowsPath(String value) {
        if (value.length() < 3 || value.charAt(1) != ':' || !isSeparator(value.charAt(2))) {
            return null;
        }
        char drive = Character.toLowerCase(value.charAt(0));
        if (drive < 'a' || drive > 'z') {
            return null;
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            return null;
        }
        String rest = value.substring(3).replace('\\', '/');
        return Path.of("/mnt/" + drive, rest).normalize();
    }

    private static boolean isSeparator(char value) {
        return value == '\\' || value == '/';
    }
}
