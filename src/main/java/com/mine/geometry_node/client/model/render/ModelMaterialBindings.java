package com.mine.geometry_node.client.model.render;

import com.mine.geometry_node.client.model.gpu.ModelGpuResource;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mojang.blaze3d.textures.GpuTextureView;

final class ModelMaterialBindings {
    private ModelMaterialBindings() { }

    static GpuTextureView baseColor(ModelGpuResource resource, StaticModelMaterial material, boolean textured) {
        return textured ? color(resource, material.baseColorTexture().imageIndex()) : null;
    }

    static GpuTextureView emissive(ModelGpuResource resource, StaticModelMaterial material, boolean textured) {
        return textured ? color(resource, material.emissiveTexture().imageIndex()) : null;
    }

    private static GpuTextureView color(ModelGpuResource resource, int imageIndex) {
        return MinecraftModelGpuAccess.textureView(resource.texture(imageIndex));
    }
}
