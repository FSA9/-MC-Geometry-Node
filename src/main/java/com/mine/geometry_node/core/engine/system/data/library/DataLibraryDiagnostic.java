package com.mine.geometry_node.core.engine.system.data.library;

/** A non-fatal problem encountered while loading one library group or entry. */
public record DataLibraryDiagnostic(String path, String message) {
}
