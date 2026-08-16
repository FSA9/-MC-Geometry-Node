package com.mine.geometry_node.core.engine.system.model.importer.protocol;

import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;

public sealed interface ModelImportResult permits ModelImportResult.Success, ModelImportResult.Failure {
    record Success(ModelDefinition definition) implements ModelImportResult {
        public Success {
            if (definition == null) throw new IllegalArgumentException("definition must not be null");
        }
    }

    record Failure(ModelImportFailure failure) implements ModelImportResult {
        public Failure {
            if (failure == null) throw new IllegalArgumentException("failure must not be null");
        }
    }
}
