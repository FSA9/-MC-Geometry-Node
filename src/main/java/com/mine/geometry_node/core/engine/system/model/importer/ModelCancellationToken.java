package com.mine.geometry_node.core.engine.system.model.importer;

@FunctionalInterface
public interface ModelCancellationToken {
    ModelCancellationToken NONE = () -> false;

    boolean isCancelled();

    default void throwIfCancelled(String location) throws ModelImportException {
        if (isCancelled()) {
            throw new ModelImportException(ModelImportFailure.simple(
                    ModelImportErrorCode.CANCELLED, location, "model import was cancelled"));
        }
    }
}
