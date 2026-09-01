package com.mine.geometry_node.core.engine.system.data.library;

import java.util.List;

public record DataLibraryLoadResult(DataLibraryDocument document, List<DataLibraryDiagnostic> diagnostics) {
    public DataLibraryLoadResult {
        diagnostics = List.copyOf(diagnostics);
    }
}
