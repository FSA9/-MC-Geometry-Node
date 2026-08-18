package com.mine.geometry_node.client.model.render.backend.host.light.contract;

/** Read-only packed-light samples indexed by canonical source/proxy vertex occurrence. */
@FunctionalInterface
public interface HostVertexLightView {
    int packedLight(int vertexOccurrence);
}
