package com.mine.geometry_node.client.runtime.model.render.pipeline;

/** Small state transaction, kept independent of OpenGL so exceptional restoration is testable. */
public final class ModelIntegerAttributeOverride {
    public interface Driver {
        boolean enabled(int attribute);
        void enabled(int attribute, boolean enabled);
        void current(int attribute, int x, int y, int z, int w);
    }

    public static void draw(Driver driver, int attribute, int packedLight, Runnable draw) {
        boolean enabled = driver.enabled(attribute);
        // Generic attribute values are not VAO state and their numeric type cannot
        // be discovered safely. We support only an enabled array: after array draws
        // its generic current value is unspecified anyway. Reject disabled arrays
        // without changing anything, rather than corrupting a caller-owned constant.
        if (!enabled) throw new IllegalStateException("Resident UV2 override requires an enabled vertex array");
        try {
            driver.enabled(attribute, false);
            driver.current(attribute, packedLight & 65535, packedLight >>> 16, 0, 1);
            draw.run();
        } finally {
            driver.enabled(attribute, true);
        }
    }

    private ModelIntegerAttributeOverride() {}
}
