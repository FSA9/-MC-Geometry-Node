package com.mine.geometry_node.client.model.render.backend.standalone;

import org.joml.Vector4f;
import com.mine.geometry_node.core.engine.system.model.domain.ModelTextureTransform;

import java.util.List;

record ModelMaterialUniform(Vector4f baseColor, Vector4f emissiveAndCutoff, Vector4f pbrFactors,
                            Vector4f texturePresence0, Vector4f texturePresence1,
                            Vector4f uvSlots0, Vector4f uvSlots1,
                            List<ModelTextureTransform> textureTransforms) {
    ModelMaterialUniform {
        textureTransforms = List.copyOf(textureTransforms);
        if (textureTransforms.size() != 5) throw new IllegalArgumentException("material requires five texture transforms");
    }
}
