package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public record ModelPrimitive(ModelPrimitiveTopology topology,
                             Map<ModelAttributeSemantic, ModelVertexAttribute> attributes,
                             ModelIndexBuffer indices, int materialIndex, ModelBounds bounds) {
    public ModelPrimitive {
        if (topology == null || attributes == null || indices == null || bounds == null) throw new IllegalArgumentException("primitive fields must not be null");
        if (materialIndex < 0) throw new IllegalArgumentException("materialIndex must not be negative");
        Map<ModelAttributeSemantic, ModelVertexAttribute> copy = new LinkedHashMap<>();
        for (Map.Entry<ModelAttributeSemantic, ModelVertexAttribute> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || !entry.getKey().equals(entry.getValue().semantic())) {
                throw new IllegalArgumentException("attribute map key must match attribute semantic");
            }
            copy.put(entry.getKey(), entry.getValue());
        }
        attributes = Collections.unmodifiableMap(copy);
    }

    public int vertexCount() {
        ModelVertexAttribute position = attributes.get(ModelAttributeSemantic.POSITION);
        return position == null ? 0 : position.elementCount();
    }

    public ModelVertexLayout vertexLayout() { return ModelVertexLayout.from(attributes.values()); }

    public int triangleCount() { return topology == ModelPrimitiveTopology.TRIANGLES ? indices.indexCount() / 3 : 0; }
}
