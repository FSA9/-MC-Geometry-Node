package com.mine.geometry_node.core.engine.system.model.domain;

public record ModelVertexLayoutElement(ModelAttributeSemantic semantic, ModelComponentType componentType,
                                       int componentCount, boolean normalized) {
    public ModelVertexLayoutElement {
        if (semantic == null || componentType == null) throw new IllegalArgumentException("layout element metadata must not be null");
        if (componentCount < 1 || componentCount > 4) throw new IllegalArgumentException("componentCount must be within [1, 4]");
    }

    public static ModelVertexLayoutElement from(ModelVertexAttribute attribute) {
        return new ModelVertexLayoutElement(attribute.semantic(), attribute.componentType(),
                attribute.componentCount(), attribute.normalized());
    }
}
