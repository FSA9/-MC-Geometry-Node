package com.mine.geometry_node.client.runtime.light.shader;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Leave unused native terrain programs for GlDevice's normal first-draw compilation. */
public final class NativeLightPreloads {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private NativeLightPreloads() {}

    public static List<RenderPipeline> filter(List<RenderPipeline> pipelines) {
        List<RenderPipeline> result = filter(pipelines, shaderPackLoaded());
        int deferred = pipelines.size() - result.size();
        if (deferred > 0) LOGGER.info(
                "Deferred {} native terrain shader preloads because an Iris shaderpack is loaded; native programs compile on first use",
                deferred);
        return result;
    }

    static List<RenderPipeline> filter(List<RenderPipeline> pipelines, boolean shaderPackLoaded) {
        if (!shaderPackLoaded) return pipelines;
        var retained = new ArrayList<>(pipelines);
        boolean changed = retained.removeIf(pipeline -> {
            var id = pipeline.getLocation();
            if (!id.getNamespace().equals("minecraft")) return false;
            return switch (id.getPath()) {
                case "pipeline/solid_terrain", "pipeline/cutout_terrain",
                        "pipeline/translucent_terrain", "pipeline/wireframe" -> true;
                default -> false;
            };
        });
        return changed ? retained : pipelines;
    }

    private static boolean shaderPackLoaded() {
        Method method = IrisAccess.CURRENT_PACK;
        if (method == null) return false;
        try {
            // The pack loads before ShaderManager preloads shaders. IrisApi's
            // isShaderPackInUse is still false until its world pipeline is built.
            return method.invoke(null) instanceof Optional<?> pack && pack.isPresent();
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            return false;
        }
    }

    private static final class IrisAccess {
        private static final Method CURRENT_PACK = findCurrentPack();

        private static Method findCurrentPack() {
            try {
                return Class.forName("net.irisshaders.iris.Iris", false,
                        NativeLightPreloads.class.getClassLoader()).getMethod("getCurrentPack");
            } catch (ReflectiveOperationException | LinkageError unavailable) {
                return null;
            }
        }
    }
}
