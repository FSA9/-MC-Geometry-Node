package com.mine.geometry_node.client.model.render.backend.host.lod;

/** Whole-model screen-space-error selector with a stable hysteresis band. */
public final class HostModelLodSelector {
    public static final double MAX_PIXEL_ERROR = 2.0;
    public static final double HYSTERESIS_RATIO = 0.20;
    private static final double MIN_DISTANCE = 1.0E-4;

    private HostModelLodSelector() {}

    public static int select(double minX, double minY, double minZ,
                             double maxX, double maxY, double maxZ,
                             double cameraX, double cameraY, double cameraZ,
                             double verticalFovDegrees, int viewportHeight,
                             double worldExtent, double[] relativeErrors, int previousLevel) {
        if (relativeErrors == null || relativeErrors.length != 4 || viewportHeight < 1
                || !Double.isFinite(verticalFovDegrees) || verticalFovDegrees <= 0.0
                || verticalFovDegrees >= 179.0 || !Double.isFinite(worldExtent) || worldExtent <= 0.0) {
            return 0;
        }
        double dx = axisDistance(cameraX, minX, maxX);
        double dy = axisDistance(cameraY, minY, maxY);
        double dz = axisDistance(cameraZ, minZ, maxZ);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= MIN_DISTANCE) return 0;
        double projectionScale = viewportHeight
                / (2.0 * Math.tan(Math.toRadians(verticalFovDegrees) * 0.5));
        double factor = worldExtent * projectionScale / distance;
        int direct = coarsestWithin(relativeErrors, factor, MAX_PIXEL_ERROR);
        if (previousLevel < 0 || previousLevel > 3) return direct;

        int selected = previousLevel;
        double refineThreshold = MAX_PIXEL_ERROR * (1.0 + HYSTERESIS_RATIO);
        while (selected > 0 && pixelError(relativeErrors[selected], factor) > refineThreshold) selected--;
        double coarsenThreshold = MAX_PIXEL_ERROR * (1.0 - HYSTERESIS_RATIO);
        while (selected < 3 && pixelError(relativeErrors[selected + 1], factor) <= coarsenThreshold) selected++;
        return selected;
    }

    private static int coarsestWithin(double[] errors, double factor, double threshold) {
        int result = 0;
        for (int level = 1; level < errors.length; level++) {
            if (pixelError(errors[level], factor) > threshold) break;
            result = level;
        }
        return result;
    }

    private static double pixelError(double objectError, double factor) {
        return !Double.isFinite(objectError) || objectError < 0.0
                ? Double.POSITIVE_INFINITY : objectError * factor;
    }

    private static double axisDistance(double value, double minimum, double maximum) {
        if (value < minimum) return minimum - value;
        return value > maximum ? value - maximum : 0.0;
    }
}
