package com.mine.geometry_node.api;

import com.mine.geometry_node.core.metadata.MetadataCapabilities;
import java.util.Set;
import java.util.function.Function;

/** Common-side addon entry point for independent tag/data target capabilities. */
public interface MetadataRegistrationContext {
    String addonId();
    <T> void register(String id, Class<T> targetType,
                     Set<MetadataCapabilities.Capability> capabilities,
                     Function<T, MetadataCapabilities.Access> resolver);
}
