package com.mine.geometry_node.core.engine.system.data.library;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** One folder in the virtual Data Library tree. A null parent identifies the root. */
public record DataLibraryFolder(UUID id, @Nullable UUID parentId, String name) {
    public DataLibraryFolder {
        Objects.requireNonNull(id, "id");
        name = DataLibraryDocument.requireName(name, "Folder name");
    }
}
