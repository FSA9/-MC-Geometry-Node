package com.mine.geometry_node.client.model.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.joml.Matrix4f;

import java.lang.reflect.*;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Optional Iris API v0 adapter. This class deliberately references Iris only by
 * reflection so the common/client loader remains valid when Iris is absent.
 * The adapter owns the process-wide registration; callers submit immutable frame
 * snapshots to the shadow callback instead of registering one callback per asset.
 */
public final class IrisModelBridge {
    private static final Object GLOBAL_LOCK = new Object();
    private static final IrisPipelineAssignments GLOBAL_MAIN_ASSIGNMENTS = new IrisPipelineAssignments();
    private static final IrisPipelineAssignments GLOBAL_SHADOW_ASSIGNMENTS = new IrisPipelineAssignments();
    private static final List<ShadowDraw> GLOBAL_SHADOW_LISTENERS = new CopyOnWriteArrayList<>();
    private static Object GLOBAL_API;
    private static Method GLOBAL_ACTIVE;
    private static Method GLOBAL_SHADOW;
    private static Method GLOBAL_ASSIGN_MAIN;
    private static Method GLOBAL_ASSIGN_SHADOW;
    private static Object GLOBAL_MAIN_ENTITIES;
    private static Object GLOBAL_MAIN_TRANSLUCENT;
    private static Object GLOBAL_MAIN_EMISSIVE;
    private static Object GLOBAL_SHADOW_ENTITIES;
    private static Class<?> GLOBAL_CALLBACK_TYPE;
    private static int GLOBAL_REVISION = -1;
    private static boolean GLOBAL_CALLBACK_REGISTERED;

    @FunctionalInterface
    public interface ShadowDraw {
        void draw(Matrix4f view, Matrix4f projection, double x, double y, double z, float tick);
    }

    private final IrisLifecycleTracker lifecycle = new IrisLifecycleTracker();
    private Method pipelineManager, pipelineGetter, reloadVersion;
    private boolean observerAttempted;
    private String observerStatus = "not initialized";
    private Object api;
    private Method active;
    private Method shadow;
    private String status = "Iris not installed";
    private int revision = -1;
    private boolean registered;
    private boolean attempted;
    private boolean callbackRegistered;
    private RenderPipeline[] opaquePipelines = new RenderPipeline[0];
    private RenderPipeline[] translucentPipelines = new RenderPipeline[0];
    private RenderPipeline[] emissivePipelines = new RenderPipeline[0];
    private RenderPipeline[] shadowPipelines = new RenderPipeline[0];
    private Method assignMain;
    private Method assignShadow;
    private Object mainEntities;
    private Object mainTranslucent;
    private Object mainEmissive;
    private Object shadowEntities;

    /** Compatibility overload used by the P0 probe. */
    public void initialize(RenderPipeline pipeline, ShadowDraw draw) {
        initialize(new RenderPipeline[]{pipeline}, new RenderPipeline[0], new RenderPipeline[]{pipeline}, draw);
    }

    /** Registers model pipelines against Iris' public API, once per client. */
    public void initialize(RenderPipeline[] opaquePipelines,
                           RenderPipeline[] translucentPipelines,
                           RenderPipeline[] shadowPipelines,
                           ShadowDraw draw) {
        initialize(opaquePipelines, translucentPipelines, new RenderPipeline[0], shadowPipelines, draw);
    }

    public void initialize(RenderPipeline[] opaquePipelines,
                           RenderPipeline[] translucentPipelines,
                           RenderPipeline[] emissivePipelines,
                           RenderPipeline[] shadowPipelines,
                           ShadowDraw draw) {
        if (attempted) return;
        attempted = true;
        this.opaquePipelines = Objects.requireNonNull(opaquePipelines, "opaquePipelines").clone();
        this.translucentPipelines = Objects.requireNonNull(translucentPipelines, "translucentPipelines").clone();
        this.emissivePipelines = Objects.requireNonNull(emissivePipelines, "emissivePipelines").clone();
        this.shadowPipelines = Objects.requireNonNull(shadowPipelines, "shadowPipelines").clone();
        Objects.requireNonNull(draw, "draw");
        synchronized (GLOBAL_LOCK) {
            try {
                if (GLOBAL_CALLBACK_REGISTERED) {
                    bindGlobal();
                    mapPipelines();
                    GLOBAL_SHADOW_LISTENERS.add(draw);
                    registered = true;
                    callbackRegistered = true;
                    status = "Iris API " + revision + ": shared shadow callback; pipeline assignments refreshed; visuals UNTESTED";
                    return;
                }
                Class<?> apiType = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiType.getMethod("getInstance").invoke(null);
                revision = (int) apiType.getMethod("getMinorApiRevision").invoke(api);
                active = apiType.getMethod("isShaderPackInUse");
                shadow = apiType.getMethod("isRenderingShadowPass");
                if (revision < 4) {
                    status = "Iris API " + revision + ": shadow callback unavailable; model remains on host fallback";
                    return;
                }
                Class<?> mainType = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
                Class<?> shadowType = Class.forName("net.irisshaders.iris.api.v0.IrisShadowProgram");
                GLOBAL_CALLBACK_TYPE = Class.forName("net.irisshaders.iris.api.v0.IrisShadowRenderCallback");
                assignMain = apiType.getMethod("assignPipeline", RenderPipeline.class, mainType);
                assignShadow = apiType.getMethod("assignPipelineShadow", RenderPipeline.class, shadowType);
                mainEntities = mainType.getField("ENTITIES").get(null);
                mainTranslucent = mainType.getField("ENTITIES_TRANSLUCENT").get(null);
                mainEmissive = mainType.getField("EMISSIVE_ENTITIES").get(null);
                shadowEntities = shadowType.getField("SHADOW_ENTITIES").get(null);
                GLOBAL_API = api;
                GLOBAL_ACTIVE = active;
                GLOBAL_SHADOW = shadow;
                GLOBAL_ASSIGN_MAIN = assignMain;
                GLOBAL_ASSIGN_SHADOW = assignShadow;
                GLOBAL_MAIN_ENTITIES = mainEntities;
                GLOBAL_MAIN_TRANSLUCENT = mainTranslucent;
                GLOBAL_MAIN_EMISSIVE = mainEmissive;
                GLOBAL_SHADOW_ENTITIES = shadowEntities;
                GLOBAL_REVISION = revision;
                mapPipelines();
                GLOBAL_SHADOW_LISTENERS.add(draw);
                Object callback = Proxy.newProxyInstance(
                        GLOBAL_CALLBACK_TYPE.getClassLoader(), new Class<?>[]{GLOBAL_CALLBACK_TYPE},
                        (proxy, method, args) -> {
                            if (method.getDeclaringClass() == Object.class) {
                                return switch (method.getName()) {
                                    case "toString" -> "GeometryNode model shadow bridge";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default -> null;
                                };
                            }
                            if ("renderShadow".equals(method.getName()) && args != null && args.length == 6) {
                                Matrix4f view = new Matrix4f((Matrix4f) args[0]);
                                Matrix4f projection = new Matrix4f((Matrix4f) args[1]);
                                for (ShadowDraw listener : GLOBAL_SHADOW_LISTENERS) {
                                    try {
                                        listener.draw(new Matrix4f(view), new Matrix4f(projection),
                                                (double) args[2], (double) args[3], (double) args[4], (float) args[5]);
                                    } catch (RuntimeException ignored) {
                                        // One optional listener must not prevent other model casters from reaching Iris.
                                    }
                                }
                            }
                            return null;
                        });
                apiType.getMethod("registerShadowRenderCallback", GLOBAL_CALLBACK_TYPE).invoke(api, callback);
                GLOBAL_CALLBACK_REGISTERED = true;
                callbackRegistered = true;
                registered = true;
                int[] mapped = mappedCounts();
                status = "Iris API " + revision + ": mapped opaque=" + mapped[0]
                        + ", translucent=" + mapped[1] + ", shadow=" + mapped[2] + ", emissive=" + mapped[3] + "; visuals UNTESTED";
            } catch (ClassNotFoundException e) {
                status = "Iris absent or required public API class unavailable: " + e.getMessage();
            } catch (ReflectiveOperationException | LinkageError e) {
                status = "Iris bridge unavailable: " + rootMessage(e);
            }
        }
    }

    private void bindGlobal() {
        api = GLOBAL_API;
        active = GLOBAL_ACTIVE;
        shadow = GLOBAL_SHADOW;
        assignMain = GLOBAL_ASSIGN_MAIN;
        assignShadow = GLOBAL_ASSIGN_SHADOW;
        mainEntities = GLOBAL_MAIN_ENTITIES;
        mainTranslucent = GLOBAL_MAIN_TRANSLUCENT;
        mainEmissive = GLOBAL_MAIN_EMISSIVE;
        shadowEntities = GLOBAL_SHADOW_ENTITIES;
        revision = GLOBAL_REVISION;
    }

    private int[] mappedCounts() {
        int opaque = 0;
        for (RenderPipeline pipeline : opaquePipelines) if (pipeline != null) opaque++;
        int translucent = 0;
        for (RenderPipeline pipeline : translucentPipelines) if (pipeline != null) translucent++;
        int shadow = 0;
        for (RenderPipeline pipeline : shadowPipelines) if (pipeline != null) shadow++;
        int emissive = 0;
        for (RenderPipeline pipeline : emissivePipelines) if (pipeline != null) emissive++;
        return new int[]{opaque, translucent, shadow, emissive};
    }

    /**
     * Iris 1.11 keeps API assignments in process-wide IrisPipelines maps. A pack/resource reload
     * replaces GPU programs, NOT those mappings; assigning again throws even for the same role.
     * Callers still invalidate model GPU resources after observing a changed pack generation.
     */
    public void refresh() {
        if (!callbackRegistered || api == null || assignMain == null || assignShadow == null) return;
        try {
            int[] mapped = mapPipelines(); // ensures only new pipeline objects are registered
            registered = true;
            status = "Iris API " + revision + ": retained process mappings opaque=" + mapped[0]
                    + ", translucent=" + mapped[1] + ", shadow=" + mapped[2] + ", emissive=" + mapped[3]
                    + "; GPU resources refreshed separately; visuals UNTESTED";
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            registered = false;
            status = "Iris refresh failed: " + rootMessage(error);
        }
    }

    private int[] mapPipelines() throws ReflectiveOperationException {
        int opaque = 0;
        for (RenderPipeline pipeline : opaquePipelines) {
            if (pipeline == null) continue;
            GLOBAL_MAIN_ASSIGNMENTS.ensure(pipeline, mainEntities, () -> assignMain.invoke(api, pipeline, mainEntities));
            opaque++;
        }
        int translucent = 0;
        for (RenderPipeline pipeline : translucentPipelines) {
            if (pipeline == null) continue;
            GLOBAL_MAIN_ASSIGNMENTS.ensure(pipeline, mainTranslucent, () -> assignMain.invoke(api, pipeline, mainTranslucent));
            translucent++;
        }
        int emissive = 0;
        for (RenderPipeline pipeline : emissivePipelines) {
            if (pipeline == null) continue;
            GLOBAL_MAIN_ASSIGNMENTS.ensure(pipeline, mainEmissive, () -> assignMain.invoke(api, pipeline, mainEmissive));
            emissive++;
        }
        int shadow = 0;
        for (RenderPipeline pipeline : shadowPipelines) {
            if (pipeline == null) continue;
            GLOBAL_SHADOW_ASSIGNMENTS.ensure(pipeline, shadowEntities, () -> assignShadow.invoke(api, pipeline, shadowEntities));
            shadow++;
        }
        return new int[]{opaque, translucent, shadow, emissive};
    }

    /** Public API lacks reload events; this isolated observer is bound to the verified Iris 1.11 lifecycle methods. */
    public boolean pollLifecycle() {
        if (api == null) return false;
        if (!observerAttempted) {
            observerAttempted = true;
            try {
                Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                pipelineManager = iris.getMethod("getPipelineManager");
                Class<?> managerType = pipelineManager.getReturnType();
                pipelineGetter = managerType.getMethod("getPipelineNullable");
                reloadVersion = managerType.getMethod("getVersionCounterForSodiumShaderReload");
                observerStatus = "Iris 1.11 internal pipeline identity/reload counter observer";
            } catch (ReflectiveOperationException | LinkageError error) {
                pipelineManager = null;
                observerStatus = "DEGRADED: only enabled changes and explicit F3+T observed; " + rootMessage(error);
            }
        }
        boolean enabled = active();
        try {
            Object manager = pipelineManager == null ? null : pipelineManager.invoke(null);
            Object pipeline = manager == null ? null : pipelineGetter.invoke(manager);
            int version = manager == null ? 0 : (int) reloadVersion.invoke(manager);
            boolean changed = lifecycle.observe(enabled, manager, pipeline, version);
            if (changed) refresh();
            return changed;
        } catch (ReflectiveOperationException | LinkageError error) {
            pipelineManager = null;
            observerStatus = "DEGRADED: lifecycle observer failed; " + rootMessage(error);
            return lifecycle.observe(enabled, null, null, 0);
        }
    }

    public Map<String, Object> lifecycleReport() {
        return Map.of("generation", lifecycle.generation(), "observer", observerStatus,
                "automaticSamePackReloadDetection", pipelineManager != null,
                "publicReloadCallbackAvailable", false,
                "mainRoutingAvailable", registered, "shadowCallbackRegistered", callbackRegistered,
                "fallbackStatus", registered ? "mapped; visual verification pending"
                        : "host fallback only; shaderpack visibility and shadows not guaranteed");
    }

    public String status() { return status; }
    public int revision() { return revision; }
    public boolean available() { return registered; }
    public boolean callbackRegistered() { return callbackRegistered; }
    public boolean active() { return query(active); }
    public boolean shadow() { return query(shadow); }

    private boolean query(Method method) {
        if (api == null || method == null) return false;
        try {
            return (boolean) method.invoke(api);
        } catch (ReflectiveOperationException | LinkageError e) {
            status = "Iris query failed: " + rootMessage(e);
            return false;
        }
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.toString();
    }
}
