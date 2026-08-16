package com.mine.geometry_node.client.model.render.backend.host.iris.shadow;

/** Narrow whitelist for non-owning Iris shadow color attachments used by the OpenGL backend. */
final class IrisShadowColorFormatPolicy {
    private IrisShadowColorFormatPolicy() {}

    static Descriptor descriptor(String name) {
        return switch (name) {
            case "RGBA", "RGBA8", "RGBA16", "RGBA16F", "RGBA32F" -> Descriptor.RGBA;
            case "R8" -> Descriptor.RED;
            default -> throw new IllegalStateException("unsupported Iris shadow color format " + name);
        };
    }

    enum Descriptor { RGBA, RED }
}
