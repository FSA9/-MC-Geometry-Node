package com.mine.geometry_node.client.model.render.backend.host.light.contract;

/** Immutable, instance-local lighting result. Implementations own their resident storage. */
public interface HostLocalLightField extends AutoCloseable {
    HostLightFieldIdentity identity();

    long residentBytes();

    @Override
    void close();
}
