package com.mine.geometry_node.client.model.render.backend.host.iris.entity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

/** Version-scoped probe for a shaderpack's dedicated translucent entity program. */
public final class IrisEntityTranslucency {
    private static Method getPipelineManager;
    private static Method getPipeline;
    private static Field resolverField;
    private static Method hasProgram;
    private static Object entitiesTranslucent;
    private static Object currentPipeline;
    private static Snapshot current = new Snapshot(false, "IRIS_PIPELINE_UNAVAILABLE");

    private IrisEntityTranslucency() {}

    public static synchronized Snapshot snapshot() {
        try {
            resolveContract();
            Object manager = getPipelineManager.invoke(null);
            Optional<?> pipeline = (Optional<?>) getPipeline.invoke(manager);
            if (pipeline.isEmpty()) return cache(null, false, "IRIS_PIPELINE_UNAVAILABLE");
            Object pipelineInstance = pipeline.get();
            if (pipelineInstance == currentPipeline) return current;
            Object resolver = resolverField.get(pipelineInstance);
            boolean dedicated = (boolean) hasProgram.invoke(resolver, entitiesTranslucent);
            return cache(pipelineInstance, dedicated,
                    dedicated ? "DEDICATED_ENTITIES_TRANSLUCENT" : "ENTITIES_TRANSLUCENT_FALLS_BACK_TO_ENTITIES");
        } catch (ReflectiveOperationException | RuntimeException failure) {
            return cache(null, false, "IRIS_TRANSLUCENCY_PROBE_FAILED:" + failure.getClass().getSimpleName());
        }
    }

    public static synchronized void clear() {
        currentPipeline = null;
        current = new Snapshot(false, "IRIS_PIPELINE_UNAVAILABLE");
    }

    private static Snapshot cache(Object pipeline, boolean dedicated, String diagnostic) {
        currentPipeline = pipeline;
        current = new Snapshot(dedicated, diagnostic);
        return current;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void resolveContract() throws ReflectiveOperationException {
        if (getPipelineManager != null) return;
        ClassLoader loader = IrisEntityTranslucency.class.getClassLoader();
        Class<?> iris = Class.forName("net.irisshaders.iris.Iris", false, loader);
        getPipelineManager = iris.getMethod("getPipelineManager");
        Class<?> manager = Class.forName("net.irisshaders.iris.pipeline.PipelineManager", false, loader);
        getPipeline = manager.getMethod("getPipeline");
        Class<?> pipeline = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline", false, loader);
        resolverField = pipeline.getDeclaredField("resolver");
        resolverField.setAccessible(true);
        Class<?> programId = Class.forName("net.irisshaders.iris.shaderpack.loading.ProgramId", false, loader);
        entitiesTranslucent = Enum.valueOf((Class<? extends Enum>) programId, "EntitiesTrans");
        Class<?> resolver = Class.forName("net.irisshaders.iris.shaderpack.programs.ProgramFallbackResolver", false, loader);
        hasProgram = resolver.getMethod("has", programId);
    }

    public record Snapshot(boolean dedicatedProgram, String diagnostic) {}
}
