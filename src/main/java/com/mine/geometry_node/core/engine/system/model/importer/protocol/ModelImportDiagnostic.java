package com.mine.geometry_node.core.engine.system.model.importer.protocol;

public record ModelImportDiagnostic(Severity severity, String code, String location, String message) {
    public ModelImportDiagnostic {
        if (severity == null) throw new IllegalArgumentException("severity must not be null");
        code = code == null ? "" : code;
        location = location == null ? "" : location;
        message = message == null ? "" : message;
    }

    public enum Severity { INFO, WARNING }
}
