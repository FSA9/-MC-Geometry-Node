package com.mine.geometry_node.core.engine.system.datalibrary.storage;

import com.mine.geometry_node.core.engine.system.datalibrary.model.DataLibraryDocument;

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

    /** Editable snapshots and uploads must never silently accept partially decoded data. */
    public DataLibraryDocument requireValidDocument() {
        if (!diagnostics.isEmpty()) {
            String details = diagnostics.stream().limit(8)
                    .map(diagnostic -> diagnostic.path() + ": " + diagnostic.message())
                    .collect(java.util.stream.Collectors.joining("; "));
            throw new IllegalArgumentException("Invalid Data Library: " + details);
        }
        return document;
    }
}
