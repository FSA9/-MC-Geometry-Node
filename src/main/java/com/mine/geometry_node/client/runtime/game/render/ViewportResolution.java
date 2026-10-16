package com.mine.geometry_node.client.runtime.game.render;

/** Uniform scale quantization; shrink waits for stability, growth/window resize is immediate. */
final class ViewportResolution {
    private static final long SHRINK_DELAY = 200_000_000L;
    private int sourceWidth, sourceHeight, currentStep, candidateStep;
    private long candidateSince;

    GameFrameImage.Size select(int width, int height, double demand, long now) {
        if (width <= 0 || height <= 0 || !Double.isFinite(demand) || demand <= 0) {
            return new GameFrameImage.Size(0, 0);
        }
        int desired = Math.clamp((int) Math.ceil(demand * 16), 1, 16);
        if (width != sourceWidth || height != sourceHeight || currentStep == 0 || desired >= currentStep) {
            sourceWidth = width; sourceHeight = height;
            currentStep = candidateStep = desired;
            candidateSince = now;
        } else {
            if (candidateStep != desired) { candidateStep = desired; candidateSince = now; }
            if (now - candidateSince >= SHRINK_DELAY) currentStep = desired;
        }
        return new GameFrameImage.Size(Math.max(1, (int) Math.ceil(width * currentStep / 16.0)),
                Math.max(1, (int) Math.ceil(height * currentStep / 16.0)));
    }
}
