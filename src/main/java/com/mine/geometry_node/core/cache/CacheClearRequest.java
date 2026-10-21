package com.mine.geometry_node.core.cache;

import java.util.Objects;

/** Namespace is interpreted by the owning adapter; null addresses all of that adapter's namespaces. */
public record CacheClearRequest(Reason reason, String namespace) {
    public enum Reason { USER, DISCONNECT, WORLD_UNLOAD, RESOURCE_RELOAD, SHUTDOWN }
    public CacheClearRequest {
        Objects.requireNonNull(reason);
        if (namespace != null && namespace.isBlank()) throw new IllegalArgumentException("Empty cache namespace");
    }
    public static CacheClearRequest user() { return of(Reason.USER); }
    public static CacheClearRequest user(String namespace) { return new CacheClearRequest(Reason.USER, namespace); }
    public static CacheClearRequest of(Reason reason) { return new CacheClearRequest(reason, null); }
}
