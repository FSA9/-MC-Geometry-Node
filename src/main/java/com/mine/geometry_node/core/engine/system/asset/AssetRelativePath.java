package com.mine.geometry_node.core.engine.system.asset;

import java.util.ArrayList;
import java.util.List;

/** Common client/server policy for canonical asset paths relative to a repository root. */
public final class AssetRelativePath {
    private AssetRelativePath() {
    }

    public static String normalize(String path, boolean allowEmpty) {
        if (path == null) throw new IllegalArgumentException("path must not be null");

        String value = path.replace('\\', '/').trim();
        if (value.indexOf('\0') >= 0) throw new IllegalArgumentException("path must not contain null characters");
        if (value.isEmpty()) {
            if (allowEmpty) return "";
            throw new IllegalArgumentException("path must not be empty");
        }
        if (value.startsWith("/") || value.matches("^[A-Za-z]:.*")) {
            throw new IllegalArgumentException("absolute paths are not allowed: " + path);
        }
        if (value.matches("^[A-Za-z][A-Za-z0-9+.-]*:/.*")) {
            throw new IllegalArgumentException("path prefixes are not allowed: " + path);
        }

        List<String> segments = new ArrayList<>();
        for (String segment : value.split("/")) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("invalid path segment: " + path);
            }
            segments.add(segment);
        }
        return String.join("/", segments);
    }
}
