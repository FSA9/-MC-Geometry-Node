package com.mine.geometry_node.client.model.render.pipeline;

/** Raw indexed blend transaction; restores every draw buffer without changing host/Iris caches. */
final class ModelIndexedBlendOverride {
    record State(boolean enabled, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha, int equationRgb, int equationAlpha) {}
    interface Driver {
        int drawBuffers();
        State state(int buffer);
        void state(int buffer, State state);
    }

    static void draw(Driver driver, State replacement, Runnable draw) {
        State[] saved = new State[driver.drawBuffers()];
        // Read the complete state first. A query failure must not leave any partial override.
        for (int i = 0; i < saved.length; i++) saved[i] = driver.state(i);
        Throwable failure = null;
        try {
            for (int i = 0; i < saved.length; i++) driver.state(i, replacement);
            draw.run();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            Throwable restoreFailure = null;
            for (int i = 0; i < saved.length; i++) {
                try { driver.state(i, saved[i]); }
                catch (RuntimeException | Error error) {
                    if (restoreFailure == null) restoreFailure = error;
                    else restoreFailure.addSuppressed(error);
                }
            }
            if (restoreFailure != null) {
                if (failure != null) failure.addSuppressed(restoreFailure);
                else if (restoreFailure instanceof RuntimeException error) throw error;
                else throw (Error) restoreFailure;
            }
        }
    }

    private ModelIndexedBlendOverride() {}
}
