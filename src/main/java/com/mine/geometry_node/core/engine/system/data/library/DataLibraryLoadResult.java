package com.mine.geometry_node.core.engine.system.data.library;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DataLibraryLoadResult(DataLibraryDocument document, List<DataLibraryDiagnostic> diagnostics,
                                    Map<UUID, String> expectedFingerprints) {
    public DataLibraryLoadResult {
        diagnostics = List.copyOf(diagnostics);
        expectedFingerprints = Map.copyOf(expectedFingerprints);
    }

    public DataLibraryLoadResult(DataLibraryDocument document, List<DataLibraryDiagnostic> diagnostics) {
        this(document, diagnostics, Map.of());
    }
}
