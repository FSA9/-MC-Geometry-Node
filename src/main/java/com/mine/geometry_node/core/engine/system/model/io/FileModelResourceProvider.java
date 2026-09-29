package com.mine.geometry_node.core.engine.system.model.io;

import com.mine.geometry_node.core.engine.system.model.asset.ModelLimits;
import com.mine.geometry_node.core.engine.system.model.diagnostic.ModelProblem;
import java.io.IOException;
import java.nio.file.*;

/** Local file provider confined to one existing real directory, including symlink targets. */
public final class FileModelResourceProvider implements ModelResourceProvider {
    private final Path root;
    private final ModelLimits limits;
    public FileModelResourceProvider(Path root) throws IOException { this(root, ModelLimits.DEFAULT); }
    public FileModelResourceProvider(Path root, ModelLimits limits) throws IOException {
        this.root = root.toRealPath(); this.limits = limits;
        if (!Files.isDirectory(this.root)) throw new IOException("model root is not a directory: " + root);
    }
    public Path root() { return root; }
    @Override public ModelResource open(String id) throws IOException {
        String normalized = ModelResourcePaths.normalize(id);
        Path target = root.resolve(normalized).normalize();
        if (!target.startsWith(root)) throw outside(id);
        Path real = target.toRealPath();
        if (!real.startsWith(root) || !Files.isRegularFile(real)) throw outside(id);
        return new ModelResource(normalized, new FileModelSource(real, limits));
    }
    private static ModelProblem outside(String id) { return new ModelProblem(ModelProblem.Code.INVALID_SOURCE, id, "resource escapes controlled root or is not a file"); }
}
