package com.mine.geometry_node.client.runtime.light.shader.adapter;

/** Read resolved pack options only when a linked receiver actually needs them. Iris remains optional. */
public final class LightShaderOptions {
    private LightShaderOptions() {}

    public static boolean booleanValue(String name, boolean fallback) {
        try {
            var current = (java.util.Optional<?>) Class.forName("net.irisshaders.iris.Iris")
                    .getMethod("getCurrentPack").invoke(null);
            if (current.isEmpty()) return fallback;
            Object pack = current.get();
            Object options = pack.getClass().getMethod("getShaderPackOptions").invoke(pack);
            Object values = options.getClass().getMethod("getOptionValues").invoke(options);
            Class<?> contract = Class.forName("net.irisshaders.iris.shaderpack.option.values.OptionValues");
            return (boolean) contract.getMethod("getBooleanValueOrDefault", String.class).invoke(values, name);
        } catch (ReflectiveOperationException | LinkageError | java.util.ServiceConfigurationError error) {
            return fallback;
        }
    }
}
