package com.mine.geometry_node.client.model.render.backend.host.iris.labpbr;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.integration.ModelCompatibilityLoss;
import com.mine.geometry_node.client.model.render.backend.host.iris.labpbr.ModelProjectorCapability;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Objects;

/** HOST_NATIVE bridge for associating LabPBR auxiliaries with a compatibility albedo. */
public final class IrisLabPbrProjector {
    private static final String REGISTRY = "net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry";
    private static final String LOADER = "net.irisshaders.iris.pbr.loader.PBRTextureLoader";
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String FORMAT_LOADER = "net.irisshaders.iris.pbr.format.TextureFormatLoader";
    private static final String LABPBR_FORMAT = "net.irisshaders.iris.pbr.format.LabPBRTextureFormat";
    private static final String IRIS = "net.irisshaders.iris.Iris";
    private static final String IRIS_PIPELINE = "net.irisshaders.iris.pipeline.IrisRenderingPipeline";
    private static long structureGeneration = Long.MIN_VALUE;
    private static Structure structure;
    private static Throwable structureFailure;
    private static Binding binding;
    private static long bindingFailureGeneration = Long.MIN_VALUE;
    private static Throwable bindingFailure;

    private IrisLabPbrProjector() {}

    /** One immutable environment observation. Call at most once for a rendered frame. */
    public static synchronized Snapshot snapshot(long generation) {
        if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
        Structure current = structure(generation);
        if (current == null) {
            ModelProjectorCapability state = structureFailure instanceof ClassNotFoundException
                    ? ModelProjectorCapability.ABSENT : ModelProjectorCapability.FAILED;
            return new Snapshot(generation, state, diagnostic(state, structureFailure));
        }
        try {
            Object api = current.apiGetInstance().invoke(null);
            if (!(boolean) current.shaderPackInUse().invoke(api)) {
                return new Snapshot(generation, ModelProjectorCapability.INACTIVE,
                        diagnostic(ModelProjectorCapability.INACTIVE, null));
            }
            Object format = current.getFormat().invoke(null);
            if (format != null) {
                if (!current.labPbrFormat().isInstance(format)
                        || !"lab-pbr".equals(current.formatName().invoke(format))
                        || !"1.3".equals(current.formatVersion().invoke(format))) {
                    return new Snapshot(generation, ModelProjectorCapability.INACTIVE,
                            diagnostic(ModelProjectorCapability.INACTIVE, null));
                }
                Throwable failure = ensureBindingForGeneration(current, generation);
                if (failure != null) {
                    return new Snapshot(generation, ModelProjectorCapability.FAILED,
                            diagnostic(ModelProjectorCapability.FAILED, failure));
                }
                return new Snapshot(generation, ModelProjectorCapability.ACTIVE,
                        diagnostic(ModelProjectorCapability.ACTIVE, null));
            }
            PipelineSignal signal = pipelineSignal(current);
            ModelProjectorCapability state = switch (signal) {
                case ABSENT -> ModelProjectorCapability.PENDING;
                case BINDS_PBR -> ModelProjectorCapability.UNVERIFIED;
                case NO_PBR -> ModelProjectorCapability.INACTIVE;
            };
            if (state == ModelProjectorCapability.UNVERIFIED) {
                Throwable failure = ensureBindingForGeneration(current, generation);
                if (failure != null) {
                    return new Snapshot(generation, ModelProjectorCapability.FAILED,
                            diagnostic(ModelProjectorCapability.FAILED, failure));
                }
            }
            return new Snapshot(generation, state, diagnostic(state, null));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return new Snapshot(generation, ModelProjectorCapability.FAILED,
                    diagnostic(ModelProjectorCapability.FAILED, exception));
        }
    }

    private static Throwable ensureBindingForGeneration(Structure current, long generation) {
        if (bindingFailureGeneration == generation) return bindingFailure;
        try {
            ensureBinding(current);
            return null;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            bindingFailureGeneration = generation;
            bindingFailure = exception;
            return exception;
        }
    }

    private static Structure structure(long generation) {
        if (structureGeneration == generation) return structure;
        structureGeneration = generation;
        structure = null;
        structureFailure = null;
        bindingFailureGeneration = Long.MIN_VALUE;
        bindingFailure = null;
        try {
            ClassLoader loader = IrisLabPbrProjector.class.getClassLoader();
            Class<?> apiClass = Class.forName(API, false, loader);
            Class<?> formatLoaderClass = Class.forName(FORMAT_LOADER, false, loader);
            Class<?> formatClass = Class.forName(LABPBR_FORMAT, false, loader);
            Class<?> irisClass = Class.forName(IRIS, false, loader);
            Class<?> pipelineClass = Class.forName(IRIS_PIPELINE, false, loader);
            var shouldBindPbr = pipelineClass.getDeclaredField("shouldBindPBR");
            shouldBindPbr.setAccessible(true);
            structure = new Structure(loader, apiClass.getMethod("getInstance"),
                    apiClass.getMethod("isShaderPackInUse"), formatLoaderClass.getMethod("getFormat"),
                    formatClass, formatClass.getMethod("name"), formatClass.getMethod("version"),
                    irisClass.getMethod("getPipelineManager"), pipelineClass, shouldBindPbr,
                    implementationVersion(apiClass));
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            structureFailure = exception;
        }
        return structure;
    }

    private static PipelineSignal pipelineSignal(Structure current) throws ReflectiveOperationException {
        Object pipelineManager = current.getPipelineManager().invoke(null);
        Object pipeline = pipelineManager.getClass().getMethod("getPipelineNullable").invoke(pipelineManager);
        if (pipeline == null) return PipelineSignal.ABSENT;
        if (!current.irisPipeline().isInstance(pipeline)) return PipelineSignal.NO_PBR;
        return current.shouldBindPbr().getBoolean(pipeline) ? PipelineSignal.BINDS_PBR : PipelineSignal.NO_PBR;
    }

    private static void ensureBinding(Structure current) throws ReflectiveOperationException {
        if (binding != null) return;
        ClassLoader loader = current.loader();
        Class<?> registryClass = Class.forName(REGISTRY, false, loader);
        Class<?> loaderClass = Class.forName(LOADER, false, loader);
        Class<?> consumerClass = Class.forName(LOADER + "$PBRTextureConsumer", false, loader);
        consumerClass.getMethod("acceptNormalTexture", AbstractTexture.class);
        consumerClass.getMethod("acceptSpecularTexture", AbstractTexture.class);
        Object registry = registryClass.getField("INSTANCE").get(null);
        Object loaderProxy = Proxy.newProxyInstance(loader, new Class<?>[]{loaderClass}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "load" -> {
                        if (args == null || args.length != 3 || !(args[0] instanceof LabPbrAlbedoTexture texture)) {
                            throw new IllegalArgumentException("unexpected Iris PBR load contract");
                        }
                        accept(args[2], "acceptNormalTexture", texture.normal());
                        accept(args[2], "acceptSpecularTexture", texture.specular());
                        yield null;
                    }
                    case "toString" -> "GeometryNode Iris LabPBR HOST_NATIVE loader";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> throw new UnsupportedOperationException("unexpected Iris PBR loader method " + method);
                });
        Class<?> managerClass = Class.forName("net.irisshaders.iris.pbr.texture.PBRTextureManager", false, loader);
        Object manager = managerClass.getField("INSTANCE").get(null);
        Class<?> holderClass = Class.forName("net.irisshaders.iris.pbr.texture.PBRTextureHolder", false, loader);
        Class<?> trackerClass = Class.forName("net.irisshaders.iris.pbr.TextureTracker", false, loader);
        Binding candidate = new Binding(manager, managerClass.getMethod("onDeleteTexture", int.class),
                managerClass.getMethod("getHolder", int.class), managerClass.getMethod("getOrLoadHolder", int.class),
                holderClass.getMethod("normalTexture"), holderClass.getMethod("specularTexture"),
                trackerClass.getField("INSTANCE").get(null), trackerClass.getMethod("getTexture", int.class));
        // Iris has no unregister operation. Publish only after the one irreversible operation succeeds.
        registryClass.getMethod("register", Class.class, loaderClass)
                .invoke(registry, LabPbrAlbedoTexture.class, loaderProxy);
        binding = candidate;
    }

    private static String implementationVersion(Class<?> apiClass) {
        Package owner = apiClass.getPackage();
        String value = owner == null ? null : owner.getImplementationVersion();
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static String diagnostic(ModelProjectorCapability state, Throwable failure) {
        String version = structure == null ? "unknown" : structure.implementationVersion();
        String suffix = failure == null ? "" : ": " + failure.getClass().getSimpleName();
        return "Iris version=" + version + " LabPBR HOST_NATIVE state=" + state + suffix;
    }

    /** Called after TextureManager.register, when the albedo has a stable GPU identity. */
    public static void afterAlbedoRegistration(LabPbrAlbedoTexture texture) {
        if (binding == null) {
            texture.holderState = HolderState.FAILED;
            return;
        }
        texture.advanceHolderState(true);
    }

    /** Adds the current runtime attachment state without claiming shaderpack consumption. */
    public static void reportHolderState(LabPbrAlbedoTexture texture,
                                         EnumSet<ModelCompatibilityLoss> losses) {
        if (binding == null) {
            losses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
            return;
        }
        texture.advanceHolderState(false);
        switch (texture.holderState) {
            case REGISTERED, PENDING -> losses.add(ModelCompatibilityLoss.PROJECTOR_HOLDER_PENDING);
            case ATTACHED -> { }
            case FAILED -> losses.add(ModelCompatibilityLoss.PROJECTOR_RUNTIME_UNAVAILABLE);
        }
    }

    public static void beforeAlbedoRelease(LabPbrAlbedoTexture texture) {
        Binding current = binding;
        if (current == null) return;
        try {
            if (texture.getTexture() instanceof GlTexture glTexture) {
                current.deleteHolder().invoke(current.manager(), glTexture.glId());
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            GeometryNode.LOGGER.warn("Could not release Iris LabPBR holder before albedo disposal", exception);
        }
    }

    private static void accept(Object consumer, String name, AbstractTexture texture) throws ReflectiveOperationException {
        if (texture != null) {
            Method method = consumer.getClass().getMethod(name, AbstractTexture.class);
            method.setAccessible(true);
            method.invoke(consumer, texture);
        }
    }

    public static final class LabPbrAlbedoTexture extends DynamicTexture {
        private final OwnedDynamicTexture normal;
        private final OwnedDynamicTexture specular;
        private boolean closed;
        private HolderState holderState = HolderState.REGISTERED;

        public LabPbrAlbedoTexture(NativeImage albedo, NativeImage normal, NativeImage specular) {
            super(() -> "GeometryNode Iris 1.11 LabPBR albedo", albedo);
            OwnedDynamicTexture createdNormal = null;
            OwnedDynamicTexture createdSpecular = null;
            try {
                createdNormal = normal == null ? null : new OwnedDynamicTexture("GeometryNode LabPBR normal", normal);
                createdSpecular = specular == null ? null : new OwnedDynamicTexture("GeometryNode LabPBR specular", specular);
            } catch (RuntimeException | Error failure) {
                if (createdSpecular != null) createdSpecular.close();
                if (createdNormal != null) createdNormal.close();
                super.close();
                throw failure;
            }
            this.normal = createdNormal;
            this.specular = createdSpecular;
        }

        AbstractTexture normal() { return normal; }
        AbstractTexture specular() { return specular; }

        private void advanceHolderState(boolean queue) {
            if (holderState == HolderState.ATTACHED || holderState == HolderState.FAILED) return;
            try {
                Binding current = binding;
                if (current == null) {
                    holderState = HolderState.FAILED;
                    return;
                }
                if (!(getTexture() instanceof GlTexture glTexture)) return;
                int glId = glTexture.glId();
                if (current.trackedTexture().invoke(current.textureTracker(), glId) != this) return;
                if (queue && holderState == HolderState.REGISTERED) {
                    current.getOrLoadHolder().invoke(current.manager(), glId);
                    holderState = HolderState.PENDING;
                    return;
                }
                if (holderState == HolderState.REGISTERED) {
                    current.getOrLoadHolder().invoke(current.manager(), glId);
                    holderState = HolderState.PENDING;
                    return;
                }
                Object holder = current.getHolder().invoke(current.manager(), glId);
                boolean normalAttached = normal == null || current.holderNormal().invoke(holder) == normal;
                boolean specularAttached = specular == null || current.holderSpecular().invoke(holder) == specular;
                if (normalAttached && specularAttached && (normal != null || specular != null)) {
                    holderState = HolderState.ATTACHED;
                }
            } catch (ReflectiveOperationException | RuntimeException exception) {
                holderState = HolderState.FAILED;
                GeometryNode.LOGGER.warn("Iris 1.11 LabPBR holder attachment probe failed", exception);
            }
        }

        @Override public void close() {
            if (!closed) {
                closed = true;
                if (normal != null) normal.close();
                if (specular != null) specular.close();
                super.close();
            }
        }
    }

    private enum HolderState { REGISTERED, PENDING, ATTACHED, FAILED }

    private enum PipelineSignal { ABSENT, BINDS_PBR, NO_PBR }

    public record Snapshot(long generation, ModelProjectorCapability capability, String diagnostic) {
        public Snapshot {
            if (generation < 0) throw new IllegalArgumentException("generation must not be negative");
            Objects.requireNonNull(capability, "capability");
            diagnostic = diagnostic == null ? "" : diagnostic;
        }
    }

    private record Structure(ClassLoader loader, Method apiGetInstance, Method shaderPackInUse, Method getFormat,
                             Class<?> labPbrFormat, Method formatName, Method formatVersion,
                             Method getPipelineManager, Class<?> irisPipeline, java.lang.reflect.Field shouldBindPbr,
                             String implementationVersion) { }

    private record Binding(Object manager, Method deleteHolder, Method getHolder, Method getOrLoadHolder,
                           Method holderNormal, Method holderSpecular, Object textureTracker,
                           Method trackedTexture) { }

    private static final class OwnedDynamicTexture extends DynamicTexture {
        private boolean closed;
        private OwnedDynamicTexture(String label, NativeImage image) { super(() -> label, image); }
        @Override public void close() {
            if (!closed) { closed = true; super.close(); }
        }
    }
}
