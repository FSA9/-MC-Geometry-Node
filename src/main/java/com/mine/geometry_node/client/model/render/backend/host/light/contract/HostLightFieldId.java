package com.mine.geometry_node.client.model.render.backend.host.light.contract;

import java.util.Objects;

/** Stable content identity and immutable revision of one prepared host light field. */
public record HostLightFieldId(String contentIdentity, long revision) {
    public HostLightFieldId {
        contentIdentity = Objects.requireNonNull(contentIdentity, "contentIdentity");
        if (contentIdentity.isBlank()) throw new IllegalArgumentException("contentIdentity must not be blank");
        if (revision < 0L) throw new IllegalArgumentException("revision must not be negative");
    }
}
