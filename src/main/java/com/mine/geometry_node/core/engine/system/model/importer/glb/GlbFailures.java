package com.mine.geometry_node.core.engine.system.model.importer.glb;

import com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportErrorCode;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportException;
import com.mine.geometry_node.core.engine.system.model.importer.protocol.ModelImportFailure;

final class GlbFailures {
    private GlbFailures() {
    }

    static ModelImportException invalid(String location, String message) {
        return failure(ModelImportErrorCode.INVALID_DATA, location, message);
    }

    static ModelImportException unsupported(String location, String message) {
        return failure(ModelImportErrorCode.UNSUPPORTED_FEATURE, location, message);
    }

    static ModelImportException reference(String location, String message) {
        return failure(ModelImportErrorCode.INVALID_REFERENCE, location, message);
    }

    static ModelImportException attribute(String location, String message) {
        return failure(ModelImportErrorCode.INVALID_ATTRIBUTE, location, message);
    }

    static ModelImportException failure(ModelImportErrorCode code, String location, String message) {
        return new ModelImportException(ModelImportFailure.simple(code, location, message));
    }
}
