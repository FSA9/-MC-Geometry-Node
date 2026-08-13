package com.mine.geometry_node.client.model.render.compat;

import com.mojang.blaze3d.systems.RenderPass;

public interface ModelShaderBackend {
    ModelShaderBackendStatus probe();

    void decorate(RenderPass pass) throws ReflectiveOperationException;

    default ModelShaderBackendStatus failed(Throwable failure) {
        return ModelShaderBackendStatus.unavailable(probe().id(), true,
                "Shader compatibility backend " + probe().id() + " failed: " + failure.getClass().getSimpleName()
                        + ": " + String.valueOf(failure.getMessage()));
    }

    static ModelShaderBackend standalone() {
        return new ModelShaderBackend() {
            private final ModelShaderBackendStatus status = ModelShaderBackendStatus.full("standalone", false,
                    "Model shader backend=standalone fidelity=FULL");

            @Override public ModelShaderBackendStatus probe() { return status; }
            @Override public void decorate(RenderPass pass) { }
        };
    }
}
