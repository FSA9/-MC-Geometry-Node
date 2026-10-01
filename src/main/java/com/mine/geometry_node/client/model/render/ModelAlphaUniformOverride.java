package com.mine.geometry_node.client.model.render;

/** Restores the real linked-program uniform even if uploading the override or drawing fails. */
final class ModelAlphaUniformOverride {
    interface Driver {
        float get(int program, int location);
        void set(int location, float value);
    }

    static void draw(Driver driver, int program, int location, float threshold, Runnable draw) {
        draw(driver, program, new int[]{location}, new float[]{threshold}, draw);
    }

    static void draw(Driver driver, int program, int[] locations, float[] thresholds, Runnable draw) {
        draw(driver, program, locations, thresholds, new float[locations.length], draw);
    }

    /** Caller-owned scratch is safe for the non-nested render-thread material scope. */
    static void draw(Driver driver, int program, int[] locations, float[] thresholds, float[] previous, Runnable draw) {
        if (locations.length != thresholds.length || previous.length != locations.length)
            throw new IllegalArgumentException("Mismatched alpha uniforms");
        for (int i = 0; i < locations.length; i++) previous[i] = driver.get(program, locations[i]);
        Throwable failure = null;
        try {
            for (int i = 0; i < locations.length; i++) driver.set(locations[i], thresholds[i]);
            draw.run();
        } catch (RuntimeException | Error error) {
            failure = error;
            throw error;
        } finally {
            Throwable restoreFailure = null;
            for (int i = 0; i < locations.length; i++) {
                try { driver.set(locations[i], previous[i]); }
                catch (RuntimeException | Error error) {
                    if (restoreFailure == null) restoreFailure = error; else restoreFailure.addSuppressed(error);
                }
            }
            if (restoreFailure != null) {
                if (failure != null) failure.addSuppressed(restoreFailure);
                else if (restoreFailure instanceof RuntimeException error) throw error;
                else throw (Error) restoreFailure;
            }
        }
    }

    private ModelAlphaUniformOverride() {}
}
