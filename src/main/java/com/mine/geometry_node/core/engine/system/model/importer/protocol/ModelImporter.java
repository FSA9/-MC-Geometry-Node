package com.mine.geometry_node.core.engine.system.model.importer.protocol;

import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;

public interface ModelImporter {
    String id();

    ModelDefinition importModel(ModelImportSource source, ModelImportSession session) throws ModelImportException;
}
