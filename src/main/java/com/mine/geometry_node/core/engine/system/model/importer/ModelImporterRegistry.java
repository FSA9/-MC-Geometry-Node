package com.mine.geometry_node.core.engine.system.model.importer;

import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.*;
import com.mine.geometry_node.core.engine.system.model.validation.ModelDefinitionValidator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ModelImporterRegistry {
    private final Map<String, ModelImporter> importers = new LinkedHashMap<>();

    public synchronized void register(ModelImporter importer) {
        if (importer == null) throw new IllegalArgumentException("importer must not be null");
        String id = ModelImporterIds.normalize(importer.id());
        ModelImporter existing = importers.get(id);
        if (existing != null && existing != importer) throw new IllegalStateException("duplicate model importer: " + id);
        importers.put(id, importer);
    }

    public synchronized List<String> registeredIds() {
        return Collections.unmodifiableList(new ArrayList<>(importers.keySet()));
    }

    public ModelImportResult importModel(String importerId, ModelImportSource source, ModelImportContext context) {
        if (source == null) throw new IllegalArgumentException("source must not be null");
        ModelImportContext effectiveContext = context == null ? ModelImportContext.defaults() : context;
        final String normalizedId;
        try {
            normalizedId = ModelImporterIds.normalize(importerId);
        } catch (IllegalArgumentException exception) {
            return failure(ModelImportErrorCode.IMPORTER_NOT_FOUND, "importer", exception.getMessage());
        }
        ModelImporter importer;
        synchronized (this) { importer = importers.get(normalizedId); }
        if (importer == null) return failure(ModelImportErrorCode.IMPORTER_NOT_FOUND, "importer", "model importer is not registered: " + normalizedId);
        try {
            ModelImportSession session = new ModelImportSession(effectiveContext);
            session.checkpoint("source");
            session.budgetTracker().claim(ModelBudgetResource.SOURCE_BYTES, source.byteSize(), "source");
            ModelDefinition definition = importer.importModel(source, session);
            if (definition == null) throw new ModelImportException(ModelImportFailure.simple(ModelImportErrorCode.INTERNAL_ERROR, "importer", "importer returned null"));
            if (!definition.source().equals(source.asset())) {
                throw new ModelImportException(ModelImportFailure.simple(ModelImportErrorCode.INVALID_REFERENCE, "source", "importer changed the source identity"));
            }
            effectiveContext.cancellation().throwIfCancelled("validation");
            ModelDefinitionValidator.validate(definition, effectiveContext.budget(), effectiveContext.cancellation());
            return new ModelImportResult.Success(definition);
        } catch (ModelImportException exception) {
            return new ModelImportResult.Failure(exception.failure());
        } catch (RuntimeException exception) {
            return new ModelImportResult.Failure(ModelImportFailure.simple(ModelImportErrorCode.INTERNAL_ERROR,
                    "importer", "unexpected importer failure: " + exception.getClass().getSimpleName()));
        }
    }

    private static ModelImportResult failure(ModelImportErrorCode code, String location, String message) {
        return new ModelImportResult.Failure(ModelImportFailure.simple(code, location, message));
    }
}
