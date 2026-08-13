package com.mine.geometry_node.core.engine.system.model.importer;

import com.mine.geometry_node.core.engine.system.model.importer.glb.GlbModelImporter;

public final class BuiltinModelImporters {
    private BuiltinModelImporters() {
    }

    public static ModelImporterRegistry createRegistry() {
        ModelImporterRegistry registry = new ModelImporterRegistry();
        registerInto(registry);
        return registry;
    }

    public static void registerInto(ModelImporterRegistry registry) {
        if (registry == null) throw new IllegalArgumentException("registry must not be null");
        registry.register(new GlbModelImporter());
    }
}
