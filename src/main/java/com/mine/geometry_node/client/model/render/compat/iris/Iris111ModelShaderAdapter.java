package com.mine.geometry_node.client.model.render.compat.iris;

import com.mine.geometry_node.client.model.render.compat.ModelShaderBackend;
import com.mine.geometry_node.client.model.render.compat.ModelShaderBackendStatus;
import com.mojang.blaze3d.systems.RenderPass;

import java.util.Optional;

/** Iris 1.11.x bridge. Every reference to Iris internals is contained in this class. */
public final class Iris111ModelShaderAdapter implements ModelShaderBackend {
    private static final String ID = "iris-1.11-custom-pass";
    private static final String API = "net.irisshaders.iris.api.v0.IrisApi";
    private static final String PASS_INTERFACE = "net.irisshaders.iris.mixinterface.RenderPassInterface";
    private static final String CUSTOM_PASS = "net.irisshaders.iris.mixinterface.CustomPass";
    private static final String SUPPORTED_VERSION = "1.11.3+mc26.1.2";

    @Override
    public ModelShaderBackendStatus probe() {
        ClassLoader loader = Iris111ModelShaderAdapter.class.getClassLoader();
        Class<?> apiClass;
        try {
            apiClass = Class.forName(API, false, loader);
        } catch (ClassNotFoundException exception) {
            return ModelShaderBackendStatus.full("standalone", false,
                    "Model shader backend=standalone fidelity=FULL");
        }
        try {
            Object api = apiClass.getMethod("getInstance").invoke(null);
            boolean active = (boolean) apiClass.getMethod("isShaderPackInUse").invoke(api);
            if (!active) return ModelShaderBackendStatus.full("standalone", false,
                    "Model shader backend=standalone fidelity=FULL (Iris shader pack inactive)");
            String version = irisVersion(loader).orElse("");
            if (!version.equals(SUPPORTED_VERSION)) {
                return ModelShaderBackendStatus.unavailable(ID, true,
                        "Iris shader pack is active, but no exact model shader adapter supports Iris version "
                                + (version.isEmpty() ? "<unknown>" : version));
            }
            Class<?> passInterface = Class.forName(PASS_INTERFACE, false, loader);
            Class<?> customPass = Class.forName(CUSTOM_PASS, false, loader);
            passInterface.getMethod("iris$setCustomPass", customPass);
            return ModelShaderBackendStatus.unavailable(ID, true,
                    "Iris " + version + " is active; its custom-pass hook requires complete GL state setup, and the "
                            + "GeometryNode full adapter is not selectable until that contract is implemented");
        } catch (ClassNotFoundException exception) {
            return ModelShaderBackendStatus.unavailable(ID, true,
                    "Iris was detected but its exact " + SUPPORTED_VERSION + " adapter contract is missing: "
                            + String.valueOf(exception.getMessage()));
        } catch (ReflectiveOperationException | LinkageError exception) {
            return ModelShaderBackendStatus.unavailable(ID, true,
                    "Iris was detected but its 1.11 custom-pass contract is unavailable: " + exception);
        }
    }

    @Override
    public void decorate(RenderPass pass) {
        throw new IllegalStateException("Iris 1.11 full adapter is not selectable");
    }

    private static Optional<String> irisVersion(ClassLoader loader) {
        try {
            Class<?> modListClass = Class.forName("net.neoforged.fml.ModList", false, loader);
            Object modList = modListClass.getMethod("get").invoke(null);
            Object containerOptional = modListClass.getMethod("getModContainerById", String.class)
                    .invoke(modList, "iris");
            if (!(containerOptional instanceof Optional<?> optional) || optional.isEmpty()) return Optional.empty();
            Object container = optional.get();
            Object modInfo = container.getClass().getMethod("getModInfo").invoke(container);
            Object version = modInfo.getClass().getMethod("getVersion").invoke(modInfo);
            return Optional.of(version.toString());
        } catch (ReflectiveOperationException | LinkageError exception) {
            return Optional.empty();
        }
    }
}
