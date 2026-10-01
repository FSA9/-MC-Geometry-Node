package com.mine.geometry_node.client.model.render;

/** Opaque replacement changes only enable bits; factors/equations must never be queried or touched. */
final class ModelOpaqueBlendOverride {
    interface Driver {
        int drawBuffers();
        boolean enabled(int buffer);
        void enabled(int buffer, boolean enabled);
    }
    static void draw(Driver driver, boolean[] saved, Runnable draw) {
        int count = driver.drawBuffers();
        if (saved.length < count) throw new IllegalArgumentException("Insufficient blend snapshot space");
        for (int i = 0; i < count; i++) saved[i] = driver.enabled(i);
        Throwable failure = null;
        try {
            for (int i = 0; i < count; i++) if (saved[i]) driver.enabled(i, false);
            draw.run();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            Throwable restoration = null;
            for (int i = 0; i < count; i++) if (saved[i]) {
                try { driver.enabled(i, true); }
                catch (RuntimeException | Error error) {
                    if (restoration == null) restoration = error; else restoration.addSuppressed(error);
                }
            }
            if (restoration != null) {
                if (failure != null) failure.addSuppressed(restoration);
                else if (restoration instanceof RuntimeException error) throw error;
                else throw (Error) restoration;
            }
        }
    }
    private ModelOpaqueBlendOverride() {}
}
