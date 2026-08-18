package com.mine.geometry_node.client.model.render.backend.host.entity;

import java.util.Objects;

/** Exact immutable identity tracked by the static build admission policy. */
public record HostStaticAdmissionKey(HostStaticVariantKey variantKey) {
    public HostStaticAdmissionKey {
        Objects.requireNonNull(variantKey, "variantKey");
    }
}
