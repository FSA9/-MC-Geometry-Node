package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportFailure;

import java.util.Objects;

/** Preserves the importer's structured failure across asynchronous resource-loading boundaries. */
public final class ModelResourceLoadException extends RuntimeException {
    private final ModelImportFailure failure;

    public ModelResourceLoadException(ModelImportFailure failure) {
        super(format(Objects.requireNonNull(failure, "failure")));
        this.failure = failure;
    }

    public ModelImportFailure failure() { return failure; }

    private static String format(ModelImportFailure failure) {
        String detail = failure.location() + ": " + failure.message();
        if (failure.actualValue() >= 0L && failure.limitValue() >= 0L) {
            detail += " (actual=" + failure.actualValue() + ", limit=" + failure.limitValue() + ")";
        }
        return detail;
    }
}
