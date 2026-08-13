package com.mine.geometry_node.core.engine.system.model.domain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public record ModelVertexLayout(List<ModelVertexLayoutElement> elements) {
    public ModelVertexLayout {
        elements = elements == null ? List.of() : List.copyOf(elements);
        if (elements.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("vertex layout contains a null element");
        }
        elements = elements.stream()
                .sorted(java.util.Comparator.comparing(ModelVertexLayoutElement::semantic))
                .toList();
        if (elements.isEmpty()) throw new IllegalArgumentException("vertex layout must not be empty");
        Set<ModelAttributeSemantic> semantics = new HashSet<>();
        for (ModelVertexLayoutElement element : elements) {
            if (!semantics.add(element.semantic())) {
                throw new IllegalArgumentException("vertex layout contains duplicate semantics");
            }
        }
        elements = List.copyOf(elements);
    }

    public static ModelVertexLayout from(Collection<ModelVertexAttribute> attributes) {
        if (attributes == null) throw new IllegalArgumentException("attributes must not be null");
        List<ModelVertexLayoutElement> elements = new ArrayList<>(attributes.size());
        for (ModelVertexAttribute attribute : attributes) elements.add(ModelVertexLayoutElement.from(attribute));
        return new ModelVertexLayout(elements);
    }
}
