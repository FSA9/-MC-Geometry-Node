package com.mine.geometry_node.core.cache;

public record CacheReport(CacheDescriptor descriptor, CacheStats stats, boolean clearable, String error) {}
