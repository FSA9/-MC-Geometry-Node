package com.mine.geometry_node.core.engine.system.model.asset;
import java.util.*;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
/** Wire/storage contract; no local absolute paths or graphics objects. */
public record ModelManifest(String entryPoint, List<Dependency> dependencies, Distribution distribution) {
    public enum Distribution { SERVER_SHARED, LOCAL_REQUIRED }
    public record Dependency(String path, long encodedBytes, String sha256) {
        public Dependency {
            requireRelative(path);
            if (encodedBytes < 0 || sha256 == null || !sha256.matches("[0-9a-f]{64}"))
                throw new IllegalArgumentException("invalid dependency");
        }
    }
    public ModelManifest {
        requireRelative(entryPoint); dependencies = List.copyOf(dependencies); Objects.requireNonNull(distribution);
        var paths = new HashSet<String>();
        for (Dependency d : dependencies) if (!paths.add(d.path())) throw new IllegalArgumentException("duplicate dependency " + d.path());
        if (!paths.contains(entryPoint)) throw new IllegalArgumentException("entry point missing from manifest");
    }
    public long validate(ModelLimits limits) throws ModelProblem {
        ModelLimits.requireWithin(dependencies.size(), limits.dependencies(), "manifest/dependencies");
        long total = 0;
        for (Dependency d : dependencies) {
            if (d.encodedBytes() > limits.packageBytes() - total)
                throw new ModelProblem(ModelProblem.Code.BUDGET_EXCEEDED, d.path(), "complete dependency package exceeds limit");
            total += d.encodedBytes();
        }
        return total;
    }
    private static void requireRelative(String value) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.contains(":") || value.contains("\\")
                || Arrays.stream(value.split("/", -1)).anyMatch(s -> s.isEmpty() || s.equals(".") || s.equals("..")))
            throw new IllegalArgumentException("invalid manifest relative path");
    }
}
