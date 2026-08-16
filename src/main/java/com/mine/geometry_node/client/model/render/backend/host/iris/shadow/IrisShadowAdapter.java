package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.backend.standalone.StandaloneModelRenderer;
import com.mine.geometry_node.client.model.render.backend.standalone.ModelPipelineKey;
import com.mine.geometry_node.client.model.render.backend.standalone.ModelShadowPhase;
import com.mine.geometry_node.client.model.render.backend.host.iris.entity.IrisEntityTranslucency;
import com.mine.geometry_node.client.model.render.integration.NativeRenderParameters;
import com.mine.geometry_node.client.model.render.integration.NativeTransparencyPolicy;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Matrix4f;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;

/** Optional bridge to Iris' public custom shadow-render API. */
public final class IrisShadowAdapter {
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String CALLBACK = "net.irisshaders.iris.api.v0.IrisShadowRenderCallback";
    private static final String PROGRAM = "net.irisshaders.iris.api.v0.IrisShadowProgram";
    private static Object callback;
    private static boolean installed;
    private static volatile String installFailure = "";
    private static volatile String opaqueFailure = "";
    private static volatile String translucentFailure = "";
    private static volatile IrisShadowCapabilities capabilities;
    private static long activeGeneration = -1;
    private static volatile int lastSubmittedDraws;
    private static volatile int lastOpaqueSubmittedDraws;
    private static volatile int lastTranslucentSubmittedDraws;
    private static volatile boolean translucentPhaseObserved;
    private static Matrix4f pendingModelView;
    private static Matrix4f pendingProjection;
    private static double pendingCameraX, pendingCameraY, pendingCameraZ;
    private static boolean translucentPending;
    private static boolean pendingOpaqueTranslucencyFallback;

    private IrisShadowAdapter() {}

    public static synchronized void install(Map<ModelPipelineKey, RenderPipeline> pipelines) {
        if (installed || !installFailure.isEmpty()) return;
        try {
            ClassLoader loader = IrisShadowAdapter.class.getClassLoader();
            Class<?> apiClass = Class.forName(API, false, loader);
            Class<?> callbackClass = Class.forName(CALLBACK, false, loader);
            Class<? extends Enum> programClass = Class.forName(PROGRAM, false, loader).asSubclass(Enum.class);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method assign = apiClass.getMethod("assignPipelineShadow", RenderPipeline.class, programClass);
            for (Map.Entry<ModelPipelineKey, RenderPipeline> entry : pipelines.entrySet()) {
                assign.invoke(api, entry.getValue(), enumValue(programClass,
                        IrisShadowProgramPolicy.programName(entry.getKey().alphaMode())));
            }
            callback = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) ->
                    invoke(proxy, method, args));
            apiClass.getMethod("registerShadowRenderCallback", callbackClass).invoke(api, callback);
            installed = true;
        } catch (ClassNotFoundException absent) {
            // Iris is optional. A later process restart is required if it is added to the mod set.
            installFailure = "IRIS_ABSENT";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            installFailure = exception.getClass().getSimpleName();
            GeometryNode.LOGGER.warn("Could not install Iris HOST_NATIVE shadow caster adapter", exception);
        }
    }

    public static boolean installed() { return installed; }
    public static int lastSubmittedDraws() { return lastSubmittedDraws; }
    public static int lastOpaqueSubmittedDraws() { return lastOpaqueSubmittedDraws; }
    public static int lastTranslucentSubmittedDraws() { return lastTranslucentSubmittedDraws; }
    public static IrisShadowCapabilities capabilities() { return capabilities; }
    public static boolean translucentPhaseObserved() { return translucentPhaseObserved; }
    public static String failure() {
        if (!installFailure.isEmpty()) return installFailure;
        if (!opaqueFailure.isEmpty()) return "OPAQUE_" + opaqueFailure;
        return translucentFailure.isEmpty() ? "" : "TRANSLUCENT_" + translucentFailure;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object enumValue(Class<? extends Enum> type, String name) {
        return Enum.valueOf(type, name);
    }

    private static Object invoke(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "renderShadow" -> {
                if (args == null || args.length != 6 || !(args[0] instanceof Matrix4f modelView)
                        || !(args[1] instanceof Matrix4f projection)) {
                    throw new IllegalArgumentException("unexpected Iris shadow callback contract");
                }
                lastOpaqueSubmittedDraws = 0;
                lastTranslucentSubmittedDraws = 0;
                lastSubmittedDraws = 0;
                try {
                    pendingModelView = new Matrix4f(modelView);
                    pendingProjection = new Matrix4f(projection);
                    pendingCameraX = (double) args[2];
                    pendingCameraY = (double) args[3];
                    pendingCameraZ = (double) args[4];
                    NativeRenderParameters parameters = NativeRenderParameters.current();
                    boolean dedicatedProgram = parameters.transparencyPolicy() == NativeTransparencyPolicy.AUTO
                            && IrisEntityTranslucency.snapshot().dedicatedProgram();
                    pendingOpaqueTranslucencyFallback = !parameters.preservesBlend(dedicatedProgram);
                    translucentPending = true;
                    IrisShadowTargetResolver.Targets targets = IrisShadowTargetResolver.resolve();
                    acceptGeneration(targets.capabilities());
                    lastOpaqueSubmittedDraws = StandaloneModelRenderer.renderShadow(modelView, projection,
                            pendingCameraX, pendingCameraY, pendingCameraZ, targets.color(), targets.depth(),
                            ModelShadowPhase.OPAQUE, pendingOpaqueTranslucencyFallback);
                    opaqueFailure = "";
                    lastSubmittedDraws = lastOpaqueSubmittedDraws;
                } catch (ReflectiveOperationException exception) {
                    translucentPending = false;
                    failOpaque("TARGET_" + exception.getClass().getSimpleName(), exception);
                } catch (RuntimeException | LinkageError exception) {
                    translucentPending = false;
                    failOpaque("DRAW_" + exception.getClass().getSimpleName(), exception);
                }
                yield null;
            }
            case "toString" -> "GeometryNode Iris HOST_NATIVE shadow callback";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> throw new UnsupportedOperationException("unexpected Iris shadow callback method " + method);
        };
    }

    /** Called by the optional Iris mixin immediately after opaque depth is copied to shadowtex1. */
    public static void renderTranslucentAfterDepthCopy() {
        if (!translucentPending) return;
        translucentPending = false;
        try {
            IrisShadowTargetResolver.Targets targets = IrisShadowTargetResolver.resolve();
            acceptGeneration(targets.capabilities());
            translucentPhaseObserved = true;
            lastTranslucentSubmittedDraws = StandaloneModelRenderer.renderShadow(
                    pendingModelView, pendingProjection,
                    pendingCameraX, pendingCameraY, pendingCameraZ, targets.color(), targets.depth(),
                    ModelShadowPhase.TRANSLUCENT, pendingOpaqueTranslucencyFallback);
            translucentFailure = "";
            lastSubmittedDraws = lastOpaqueSubmittedDraws + lastTranslucentSubmittedDraws;
        } catch (ReflectiveOperationException exception) {
            failTranslucent("SHADOW_TARGET_" + exception.getClass().getSimpleName(), exception);
        } catch (RuntimeException | LinkageError exception) {
            failTranslucent("SHADOW_DRAW_" + exception.getClass().getSimpleName(), exception);
        }
    }

    private static void failTranslucent(String code, Throwable exception) {
        if (!code.equals(translucentFailure)) {
            translucentFailure = code;
            GeometryNode.LOGGER.warn("Could not submit Iris translucent shadow phase", exception);
        }
    }

    private static void failOpaque(String code, Throwable exception) {
        if (!code.equals(opaqueFailure)) {
            opaqueFailure = code;
            GeometryNode.LOGGER.warn("Could not submit Iris opaque shadow phase", exception);
        }
    }

    private static void acceptGeneration(IrisShadowCapabilities next) {
        capabilities = next;
        if (activeGeneration == next.generation()) return;
        activeGeneration = next.generation();
        opaqueFailure = "";
        translucentFailure = "";
        translucentPhaseObserved = false;
    }
}
