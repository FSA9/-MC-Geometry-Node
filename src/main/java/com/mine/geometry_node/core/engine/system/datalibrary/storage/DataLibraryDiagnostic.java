package com.mine.geometry_node.core.engine.system.datalibrary.storage;

/** A non-fatal problem encountered while loading one library group or entry. */
public record DataLibraryDiagnostic(String path, String message) {
}
