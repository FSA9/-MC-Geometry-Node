package com.mine.geometry_node.core.engine.system.datalibrary.model;

import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;
import java.util.List;

/** Canonical tag representation shared by persistence and editor models. */
final class DataLibraryTags {
    private DataLibraryTags() {}

    static List<String> normalize(List<String> tags) {
        return TagSet.normalize(tags, TagPolicy.DATA_LIBRARY_LEGACY);
    }
}
