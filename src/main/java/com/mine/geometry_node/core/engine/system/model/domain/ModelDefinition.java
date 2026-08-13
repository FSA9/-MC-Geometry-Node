package com.mine.geometry_node.core.engine.system.model.domain;

import com.mine.geometry_node.core.engine.system.model.api.ModelAssetReference;
import com.mine.geometry_node.core.engine.system.model.domain.animation.ModelAnimation;

import java.util.List;

public record ModelDefinition(
        ModelAssetReference source,
        List<ModelScene> scenes,
        int defaultScene,
        List<ModelNode> nodes,
        List<ModelMesh> meshes,
        List<ModelMaterial> materials,
        List<ModelTexture> textures,
        List<ModelImageSource> images,
        List<ModelAnimation> animations,
        List<ModelSkin> skins,
        ModelBounds bounds
) {
    public ModelDefinition {
        if (source == null || bounds == null) throw new IllegalArgumentException("model source and bounds must not be null");
        scenes = copy(scenes); nodes = copy(nodes); meshes = copy(meshes); materials = copy(materials);
        textures = copy(textures); images = copy(images); animations = copy(animations); skins = copy(skins);
        if (scenes.isEmpty()) throw new IllegalArgumentException("model must contain at least one scene");
        if (defaultScene < 0 || defaultScene >= scenes.size()) throw new IllegalArgumentException("defaultScene is out of range");
        if (materials.isEmpty()) throw new IllegalArgumentException("model must contain at least one material");
    }

    public ModelDefinition(ModelAssetReference source, List<ModelScene> scenes, int defaultScene,
                           List<ModelNode> nodes, List<ModelMesh> meshes, List<ModelMaterial> materials,
                           List<ModelTexture> textures, List<ModelImageSource> images,
                           List<ModelAnimation> animations, ModelBounds bounds) {
        this(source, scenes, defaultScene, nodes, meshes, materials, textures, images, animations, List.of(), bounds);
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
