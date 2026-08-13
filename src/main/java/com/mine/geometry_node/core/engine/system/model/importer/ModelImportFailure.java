package com.mine.geometry_node.core.engine.system.model.importer;

public record ModelImportFailure(ModelImportErrorCode code, String location, String message,
                                 long actualValue, long limitValue) {
    public ModelImportFailure {
        if (code == null) throw new IllegalArgumentException("error code must not be null");
        location = location == null ? "" : location;
        message = message == null ? "" : message;
    }

    public static ModelImportFailure simple(ModelImportErrorCode code, String location, String message) {
        return new ModelImportFailure(code, location, message, -1L, -1L);
    }
}
