package com.mine.geometry_node.core.engine.system.model.io;

import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.net.URI;
import java.util.ArrayDeque;

/** URI paths use '/' on every host. A provider never receives a scheme or an escaping path. */
public final class ModelResourcePaths {
    private ModelResourcePaths() { }
    public static String normalize(String logicalId) throws ModelProblem {
        if (logicalId == null || logicalId.isBlank() || logicalId.startsWith("/") || logicalId.indexOf('\\') >= 0
                || logicalId.indexOf(':') >= 0 || logicalId.indexOf('\0') >= 0)
            throw invalid(logicalId, "invalid logical path");
        ArrayDeque<String> parts = new ArrayDeque<>();
        for (String part : logicalId.split("/", -1)) {
            if (part.isEmpty() || part.equals(".")) continue;
            if (part.equals("..")) { if (parts.isEmpty()) throw invalid(logicalId, "path escapes provider root"); parts.removeLast(); }
            else parts.addLast(part);
        }
        if (parts.isEmpty()) throw invalid(logicalId, "empty logical path");
        return String.join("/", parts);
    }
    public static String resolve(String entry, String reference) throws ModelProblem {
        try {
            URI uri = new URI(reference);
            if (uri.isAbsolute() || uri.getRawAuthority() != null || uri.getQuery() != null || uri.getFragment() != null
                    || uri.getPath() == null || uri.getPath().isBlank() || uri.getPath().startsWith("/"))
                throw invalid(reference, "only relative asset URI paths are accepted");
            String base = normalize(entry);
            int slash = base.lastIndexOf('/');
            return normalize((slash < 0 ? "" : base.substring(0, slash + 1)) + uri.getPath());
        } catch (java.net.URISyntaxException e) { throw invalid(reference, "malformed relative URI"); }
    }
    private static ModelProblem invalid(String id, String message) { return new ModelProblem(ModelProblem.Code.INVALID_SOURCE, String.valueOf(id), message); }
}
