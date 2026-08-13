package com.mine.geometry_node.client.model.render.compat.iris;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.client.model.render.compat.ModelCompatibilityLoss;
import com.mine.geometry_node.client.model.render.compat.ModelProjectorCapability;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.opengl.GlTexture;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.EnumSet;
import java.util.Optional;

/** Exact Iris 1.11 bridge for associating LabPBR auxiliaries with a compatibility albedo. */
public final class Iris111LabPbrProjector {
    private static final String REGISTRY = "net.irisshaders.iris.pbr.loader.PBRTextureLoaderRegistry";
    private static final String LOADER = "net.irisshaders.iris.pbr.loader.PBRTextureLoader";
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String FORMAT_LOADER = "net.irisshaders.iris.pbr.format.TextureFormatLoader";
    private static final String LABPBR_FORMAT = "net.irisshaders.iris.pbr.format.LabPBRTextureFormat";
    private static final String IRIS = "net.irisshaders.iris.Iris";
    private static final String IRIS_PIPELINE = "net.irisshaders.iris.pipeline.IrisRenderingPipeline";
    private static boolean probed;
    private static boolean available;
    private static boolean probeFailed;
    private static Method deleteHolder;
    private static Method getHolder;
    private static Method getOrLoadHolder;
    private static Method holderNormal;
    private static Method holderSpecular;
    private static Method trackedTexture;
    private static Object textureTracker;
    private static Object manager;

    private Iris111LabPbrProjector() {}

    public static synchronized boolean available() {
        if (probed) return available;
        probed = true;
        try {
            ClassLoader classLoader = Iris111LabPbrProjector.class.getClassLoader();
            if (!irisVersion().equals(Optional.of("1.11.3+mc26.1.2"))) return false;
            Class<?> registryClass = Class.forName(REGISTRY, false, classLoader);
            Class<?> loaderClass = Class.forName(LOADER, false, classLoader);
            Class<?> consumerClass = Class.forName(LOADER + "$PBRTextureConsumer", false, classLoader);
            consumerClass.getMethod("acceptNormalTexture", AbstractTexture.class);
            consumerClass.getMethod("acceptSpecularTexture", AbstractTexture.class);
            Object registry = registryClass.getField("INSTANCE").get(null);
            Object loader = Proxy.newProxyInstance(classLoader, new Class<?>[]{loaderClass}, (proxy, method, args) -> {
                return switch (method.getName()) {
                    case "load" -> {
                        if (args == null || args.length != 3 || !(args[0] instanceof LabPbrAlbedoTexture texture))
                            throw new IllegalArgumentException("unexpected Iris PBR load contract");
                        accept(args[2], "acceptNormalTexture", texture.normal());
                        accept(args[2], "acceptSpecularTexture", texture.specular());
                        yield null;
                    }
                    case "toString" -> "GeometryNode Iris 1.11 LabPBR loader";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
                    default -> throw new UnsupportedOperationException("unexpected Iris PBR loader method " + method);
                };
            });
            Method register = registryClass.getMethod("register", Class.class, loaderClass);
            Class<?> managerClass = Class.forName("net.irisshaders.iris.pbr.texture.PBRTextureManager", false, classLoader);
            manager = managerClass.getField("INSTANCE").get(null);
            deleteHolder = managerClass.getMethod("onDeleteTexture", int.class);
            getHolder = managerClass.getMethod("getHolder", int.class);
            getOrLoadHolder = managerClass.getMethod("getOrLoadHolder", int.class);
            Class<?> holderClass = Class.forName("net.irisshaders.iris.pbr.texture.PBRTextureHolder", false,
                    classLoader);
            holderNormal = holderClass.getMethod("normalTexture");
            holderSpecular = holderClass.getMethod("specularTexture");
            Class<?> trackerClass = Class.forName("net.irisshaders.iris.pbr.TextureTracker", false, classLoader);
            textureTracker = trackerClass.getField("INSTANCE").get(null);
            trackedTexture = trackerClass.getMethod("getTexture", int.class);
            // Register last: Iris exposes no unregister operation for loader classes.
            register.invoke(registry, LabPbrAlbedoTexture.class, loader);
            available = true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            probeFailed = true;
            GeometryNode.LOGGER.warn("Iris 1.11 LabPBR compatibility projector is unavailable", exception);
        }
        return available;
    }

    /** Live capability: intentionally not cached across shader-pack toggles or resource reloads. */
    public static ModelProjectorCapability capability() {
        try {
            if (!available()) {
                return probeFailed ? ModelProjectorCapability.RUNTIME_FAILED : ModelProjectorCapability.INACTIVE;
            }
            ClassLoader loader = Iris111LabPbrProjector.class.getClassLoader();
            Class<?> apiClass = Class.forName(API, false, loader);
            Object api = apiClass.getMethod("getInstance").invoke(null);
            if (!(boolean) apiClass.getMethod("isShaderPackInUse").invoke(api)) {
                return ModelProjectorCapability.INACTIVE;
            }
            Class<?> formatLoaderClass = Class.forName(FORMAT_LOADER, false, loader);
            Object format = formatLoaderClass.getMethod("getFormat").invoke(null);
            if (format != null) {
                if (!Class.forName(LABPBR_FORMAT, false, loader).isInstance(format)) {
                    return ModelProjectorCapability.INACTIVE;
                }
                Object name = format.getClass().getMethod("name").invoke(format);
                Object version = format.getClass().getMethod("version").invoke(format);
                return "lab-pbr".equals(name) && "1.3".equals(version)
                        ? ModelProjectorCapability.ACTIVE : ModelProjectorCapability.INACTIVE;
            }
            return activePipelineBindsPbr(loader)
                    ? ModelProjectorCapability.ACTIVE : ModelProjectorCapability.INACTIVE;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return ModelProjectorCapability.RUNTIME_FAILED;
        }
    }

    private static boolean activePipelineBindsPbr(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> irisClass = Class.forName(IRIS, false, loader);
        Object pipelineManager = irisClass.getMethod("getPipelineManager").invoke(null);
        Object pipeline = pipelineManager.getClass().getMethod("getPipelineNullable").invoke(pipelineManager);
        Class<?> pipelineClass = Class.forName(IRIS_PIPELINE, false, loader);
        if (!pipelineClass.isInstance(pipeline)) return false;
        var field = pipelineClass.getDeclaredField("shouldBindPBR");
        field.setAccessible(true);
        return field.getBoolean(pipeline);
    }

    /** Called after TextureManager.register, when the albedo has a stable GPU identity. */
    public static void afterAlbedoRegistration(LabPbrAlbedoTexture texture) {
        if (!available()) {
            texture.holderState = HolderState.FAILED;
            return;
        }
        texture.advanceHolderState(true);
    }

    /** Adds the current runtime attachment state without claiming shaderpack consumption. */
    public static void reportHolderState(LabPbrAlbedoTexture texture,
                                         EnumSet<ModelCompatibilityLoss> losses) {
        if (!available()) {
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
        if (!available || deleteHolder == null || manager == null) return;
        try {
            if (texture.getTexture() instanceof GlTexture glTexture) deleteHolder.invoke(manager, glTexture.glId());
        } catch (ReflectiveOperationException | RuntimeException exception) {
            GeometryNode.LOGGER.warn("Could not release Iris LabPBR holder before albedo disposal", exception);
        }
    }

    private static Optional<String> irisVersion() {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList");
            Object modList = modListClass.getMethod("get").invoke(null);
            Object optional = modListClass.getMethod("getModContainerById", String.class).invoke(modList, "iris");
            if (!(optional instanceof Optional<?> value) || value.isEmpty()) return Optional.empty();
            Object info = value.get().getClass().getMethod("getModInfo").invoke(value.get());
            return Optional.of(info.getClass().getMethod("getVersion").invoke(info).toString());
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
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
                if (!(getTexture() instanceof GlTexture glTexture)) return;
                int glId = glTexture.glId();
                if (trackedTexture.invoke(textureTracker, glId) != this) return;
                if (queue && holderState == HolderState.REGISTERED) {
                    getOrLoadHolder.invoke(manager, glId);
                    holderState = HolderState.PENDING;
                    return;
                }
                if (holderState == HolderState.REGISTERED) {
                    getOrLoadHolder.invoke(manager, glId);
                    holderState = HolderState.PENDING;
                    return;
                }
                Object holder = getHolder.invoke(manager, glId);
                boolean normalAttached = normal == null || holderNormal.invoke(holder) == normal;
                boolean specularAttached = specular == null || holderSpecular.invoke(holder) == specular;
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

    private static final class OwnedDynamicTexture extends DynamicTexture {
        private boolean closed;
        private OwnedDynamicTexture(String label, NativeImage image) { super(() -> label, image); }
        @Override public void close() {
            if (!closed) { closed = true; super.close(); }
        }
    }
}
