package com.mine.geometry_node.client.model.gpu.minecraft;

import com.mine.geometry_node.core.engine.system.model.domain.*;
import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.*;
import org.lwjgl.opengl.GL33C;

import java.util.HashMap;
import java.util.Map;

/** Exact glTF sampler adapter isolated from the format-independent model packages. */
public final class MinecraftModelSamplerCache implements AutoCloseable {
    private final Map<ModelTextureSampler, GpuSampler> samplers = new HashMap<>();

    public GpuSampler get(ModelTextureSampler descriptor) {
        RenderSystem.assertOnRenderThread();
        return samplers.computeIfAbsent(descriptor, this::create);
    }

    private GpuSampler create(ModelTextureSampler descriptor) {
        GlSampler sampler = new GlSampler(AddressMode.REPEAT, AddressMode.REPEAT,
                linear(descriptor.minFilter()) ? FilterMode.LINEAR : FilterMode.NEAREST,
                descriptor.magFilter() == ModelTextureFilter.LINEAR ? FilterMode.LINEAR : FilterMode.NEAREST,
                1, java.util.OptionalDouble.empty());
        GL33C.glSamplerParameteri(sampler.getId(), GL33C.GL_TEXTURE_WRAP_S,
                GltfSamplerGlConstants.wrap(descriptor.wrapS()));
        GL33C.glSamplerParameteri(sampler.getId(), GL33C.GL_TEXTURE_WRAP_T,
                GltfSamplerGlConstants.wrap(descriptor.wrapT()));
        GL33C.glSamplerParameteri(sampler.getId(), GL33C.GL_TEXTURE_MIN_FILTER,
                GltfSamplerGlConstants.min(descriptor.minFilter()));
        GL33C.glSamplerParameteri(sampler.getId(), GL33C.GL_TEXTURE_MAG_FILTER,
                GltfSamplerGlConstants.mag(descriptor.magFilter()));
        return sampler;
    }

    private static boolean linear(ModelTextureFilter filter) {
        return filter == ModelTextureFilter.LINEAR || filter == ModelTextureFilter.LINEAR_MIPMAP_NEAREST
                || filter == ModelTextureFilter.LINEAR_MIPMAP_LINEAR;
    }

    @Override
    public void close() {
        RenderSystem.assertOnRenderThread();
        samplers.values().forEach(GpuSampler::close);
        samplers.clear();
    }
}
