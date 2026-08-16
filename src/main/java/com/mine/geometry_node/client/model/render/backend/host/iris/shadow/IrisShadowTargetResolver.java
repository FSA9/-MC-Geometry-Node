package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.textures.TextureFormat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Version-scoped bridge for the attachment omitted from Iris' public shadow callback contract. */
final class IrisShadowTargetResolver {
    private static final String IRIS = "net.irisshaders.iris.Iris";
    private static Method getPipelineManager;
    private static Method getPipeline;
    private static Field shadowRenderTargets;
    private static Method getDepthTexture;
    private static Method getColorTextureId;
    private static Method getColorTextureFormat;
    private static Method getNumColorTextures;
    private static Object currentPipeline;
    private static Object currentDepth;
    private static int currentColorId;
    private static String currentColorFormat = "";
    private static GpuTextureView depthView;
    private static GpuTextureView colorView;
    private static long generation;
    private static IrisShadowCapabilities capabilities;

    private IrisShadowTargetResolver() {}

    static Targets resolve() throws ReflectiveOperationException {
        RenderSystem.assertOnRenderThread();
        resolveContract();
        Object manager = getPipelineManager.invoke(null);
        Optional<?> pipeline = (Optional<?>) getPipeline.invoke(manager);
        if (pipeline.isEmpty()) throw new IllegalStateException("Iris has no active rendering pipeline");
        Object pipelineInstance = pipeline.get();
        Object targets = shadowRenderTargets.get(pipelineInstance);
        if (targets == null) throw new IllegalStateException("Iris shadow targets are not initialized");
        GpuTexture depth = (GpuTexture) getDepthTexture.invoke(targets);
        if (depth == null || depth.isClosed()) throw new IllegalStateException("Iris shadow depth texture is unavailable");
        int colorId = (int) getColorTextureId.invoke(targets, 0);
        if (colorId <= 0) throw new IllegalStateException("Iris shadowcolor0 texture is unavailable");
        int colorCount = (int) getNumColorTextures.invoke(targets);
        if (colorCount < 1) throw new IllegalStateException("Iris shadow pass has no color attachment");
        List<String> colorFormats = new ArrayList<>(colorCount);
        for (int slot = 0; slot < colorCount; slot++) {
            Object externalFormat = getColorTextureFormat.invoke(targets, slot);
            colorFormats.add(externalFormat instanceof Enum<?> value ? value.name() : String.valueOf(externalFormat));
        }
        String formatName = colorFormats.getFirst();
        TextureFormat format = mapColorFormat(formatName);
        if (pipelineInstance != currentPipeline || depth != currentDepth || colorId != currentColorId
                || !formatName.equals(currentColorFormat)
                || capabilities == null || !colorFormats.equals(capabilities.colorFormats())
                || depthView == null || depthView.isClosed() || colorView == null || colorView.isClosed()) {
            rebuild(pipelineInstance, depth, colorId, format, formatName, colorFormats);
        }
        return new Targets(colorView, depthView, capabilities);
    }

    private static void resolveContract() throws ReflectiveOperationException {
        if (getPipelineManager != null) return;
        ClassLoader loader = IrisShadowTargetResolver.class.getClassLoader();
        Class<?> iris = Class.forName(IRIS, false, loader);
        getPipelineManager = iris.getMethod("getPipelineManager");
        Class<?> manager = Class.forName("net.irisshaders.iris.pipeline.PipelineManager", false, loader);
        getPipeline = manager.getMethod("getPipeline");
        Class<?> pipeline = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline", false, loader);
        shadowRenderTargets = pipeline.getDeclaredField("shadowRenderTargets");
        shadowRenderTargets.setAccessible(true);
        Class<?> targets = Class.forName("net.irisshaders.iris.shadows.ShadowRenderTargets", false, loader);
        getDepthTexture = targets.getMethod("getDepthTexture");
        getColorTextureId = targets.getMethod("getColorTextureId", int.class);
        getColorTextureFormat = targets.getMethod("getColorTextureFormat", int.class);
        getNumColorTextures = targets.getMethod("getNumColorTextures");
    }

    private static TextureFormat mapColorFormat(String name) {
        return switch (IrisShadowColorFormatPolicy.descriptor(name)) {
            case RGBA -> TextureFormat.RGBA8;
            case RED -> TextureFormat.RED8;
        };
    }

    private static void rebuild(Object pipeline, GpuTexture depth, int colorId, TextureFormat format,
                                String formatName, List<String> colorFormats) {
        closeViews();
        depthView = RenderSystem.getDevice().createTextureView(depth);
        colorView = RenderSystem.getDevice().createTextureView(new BorrowedGlTexture(colorId,
                depth.getWidth(0), depth.getHeight(0), format));
        currentPipeline = pipeline;
        currentDepth = depth;
        currentColorId = colorId;
        currentColorFormat = formatName;
        capabilities = new IrisShadowCapabilities(++generation, colorFormats.size(), colorFormats, true);
    }

    private static void closeViews() {
        if (colorView != null && !colorView.isClosed()) colorView.close();
        if (depthView != null && !depthView.isClosed()) depthView.close();
        colorView = null;
        depthView = null;
        currentDepth = null;
        currentPipeline = null;
        currentColorId = 0;
        currentColorFormat = "";
        capabilities = null;
    }

    record Targets(GpuTextureView color, GpuTextureView depth, IrisShadowCapabilities capabilities) {}

    /** Non-owning wrapper; Iris retains ownership of the external texture id. */
    private static final class BorrowedGlTexture extends GlTexture {
        private BorrowedGlTexture(int id, int width, int height, TextureFormat format) {
            super(GpuTexture.USAGE_RENDER_ATTACHMENT, "GeometryNode borrowed Iris shadowcolor0",
                    format, width, height, 1, 1, id);
        }

        @Override public void close() {
            // The GlTextureView owns only its temporary FBO. Iris owns and deletes the texture.
        }

        @Override public boolean isClosed() { return false; }
    }
}
