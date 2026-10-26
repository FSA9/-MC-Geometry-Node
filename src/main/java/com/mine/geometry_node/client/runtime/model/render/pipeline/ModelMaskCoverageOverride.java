package com.mine.geometry_node.client.runtime.model.render.pipeline;

/** Integer uniform transaction kept separate from float alpha-threshold uniforms. */
public final class ModelMaskCoverageOverride {
    public interface Driver {
        int get(int program, int location);
        void set(int location, int value);
    }
    public static void draw(Driver driver, int program, int location, Runnable draw) {
        int old = driver.get(program, location);
        try { driver.set(location, 1); draw.run(); }
        finally { driver.set(location, old); }
    }
    private ModelMaskCoverageOverride() {}
}
