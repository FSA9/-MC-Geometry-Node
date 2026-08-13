package com.mine.geometry_node.core.engine.system.model.importer;

public final class ModelImportException extends Exception {
    private final ModelImportFailure failure;

    public ModelImportException(ModelImportFailure failure) {
        super(failure == null ? "model import failed" : failure.message());
        this.failure = failure == null
                ? ModelImportFailure.simple(ModelImportErrorCode.INTERNAL_ERROR, "", "model import failed")
                : failure;
    }

    public ModelImportException(ModelImportFailure failure, Throwable cause) {
        super(failure == null ? "model import failed" : failure.message(), cause);
        this.failure = failure == null
                ? ModelImportFailure.simple(ModelImportErrorCode.INTERNAL_ERROR, "", "model import failed")
                : failure;
    }

    public ModelImportFailure failure() { return failure; }
}
