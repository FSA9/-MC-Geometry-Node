package com.mine.geometry_node.core.cache;

import java.util.List;

public record CacheClearResult(CacheDescriptor descriptor, int cleared, int skipped, List<String> errors, long retainedEntries) {
    public CacheClearResult {
        errors = List.copyOf(errors);
        if (retainedEntries < -1) throw new IllegalArgumentException("Invalid retained count");
    }
    public CacheClearResult(CacheDescriptor descriptor, int cleared, int skipped, List<String> errors) {
        this(descriptor, cleared, skipped, errors, -1);
    }
}
