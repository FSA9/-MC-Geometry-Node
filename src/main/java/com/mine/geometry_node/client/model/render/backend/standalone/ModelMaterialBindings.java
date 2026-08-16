package com.mine.geometry_node.client.model.render.backend.standalone;

import com.mine.geometry_node.client.model.gpu.ModelGpuResource;
import com.mine.geometry_node.client.model.gpu.minecraft.MinecraftModelGpuAccess;
import com.mine.geometry_node.client.model.runtime.StaticModelMaterial;
import com.mine.geometry_node.client.model.runtime.StaticModelTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mine.geometry_node.client.model.gpu.ModelGpuTextureKey;
import com.mine.geometry_node.client.model.gpu.ModelTextureColorSpace;

final class ModelMaterialBindings {
    private ModelMaterialBindings() { }

    static GpuTextureView baseColor(ModelGpuResource resource, StaticModelMaterial material, boolean textured) {
        return textured ? color(resource, material.baseColorTexture().imageIndex()) : null;
    }

    static GpuTextureView emissive(ModelGpuResource resource, StaticModelMaterial material, boolean textured) {
        return textured ? color(resource, material.emissiveTexture().imageIndex()) : null;
    }

    static GpuTextureView metallicRoughness(ModelGpuResource resource, StaticModelMaterial material) {
        return data(resource, material.metallicRoughnessTexture(), ModelTextureColorSpace.LINEAR_DATA);
    }

    static GpuTextureView normal(ModelGpuResource resource, StaticModelMaterial material) {
        return data(resource, material.normalTexture(), ModelTextureColorSpace.NORMAL_VECTOR);
    }

    static GpuTextureView occlusion(ModelGpuResource resource, StaticModelMaterial material) {
        return data(resource, material.occlusionTexture(), ModelTextureColorSpace.LINEAR_DATA);
    }

    private static GpuTextureView data(ModelGpuResource resource, StaticModelTexture texture,
                                       ModelTextureColorSpace colorSpace) {
        return texture.present() ? MinecraftModelGpuAccess.textureView(resource.texture(
                new ModelGpuTextureKey(texture.imageIndex(), colorSpace))) : null;
    }

    private static GpuTextureView color(ModelGpuResource resource, int imageIndex) {
        return MinecraftModelGpuAccess.textureView(resource.texture(imageIndex));
    }
}
