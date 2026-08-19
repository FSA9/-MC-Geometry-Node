package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import java.util.Arrays;

/** Immutable block-local light-occlusion shape represented by bounded AABBs. */
public final class WorldOccluderShape {
    public static final int MAX_BOXES = 64;
    private static final double SEGMENT_EPSILON = 1.0e-7;

    private final float[] boxes;
    private final boolean conservativeFallback;

    public WorldOccluderShape(float[] boxes, boolean conservativeFallback) {
        if (boxes == null || boxes.length == 0 || boxes.length % 6 != 0
                || boxes.length / 6 > MAX_BOXES) {
            throw new IllegalArgumentException("shape must contain 1.." + MAX_BOXES + " AABBs");
        }
        this.boxes = Arrays.copyOf(boxes, boxes.length);
        for (int offset = 0; offset < this.boxes.length; offset += 6) {
            for (int axis = 0; axis < 3; axis++) {
                float minimum = this.boxes[offset + axis];
                float maximum = this.boxes[offset + 3 + axis];
                if (!Float.isFinite(minimum) || !Float.isFinite(maximum)
                        || minimum < 0F || maximum > 1F || minimum >= maximum) {
                    throw new IllegalArgumentException("shape AABBs must have positive volume inside [0, 1]");
                }
            }
        }
        this.conservativeFallback = conservativeFallback;
    }

    public static WorldOccluderShape fullCube(boolean conservativeFallback) {
        return new WorldOccluderShape(new float[]{0, 0, 0, 1, 1, 1}, conservativeFallback);
    }

    public int boxCount() { return boxes.length / 6; }
    public boolean conservativeFallback() { return conservativeFallback; }
    public float minX(int box) { return boxes[offset(box)]; }
    public float minY(int box) { return boxes[offset(box) + 1]; }
    public float minZ(int box) { return boxes[offset(box) + 2]; }
    public float maxX(int box) { return boxes[offset(box) + 3]; }
    public float maxY(int box) { return boxes[offset(box) + 4]; }
    public float maxZ(int box) { return boxes[offset(box) + 5]; }
    public long residentBytes() { return (long) boxes.length * Float.BYTES; }

    public boolean contains(float x, float y, float z) {
        for (int offset = 0; offset < boxes.length; offset += 6) {
            if (x >= boxes[offset] && x <= boxes[offset + 3]
                    && y >= boxes[offset + 1] && y <= boxes[offset + 4]
                    && z >= boxes[offset + 2] && z <= boxes[offset + 5]) return true;
        }
        return false;
    }

    boolean intersectsOpenSegment(double fromX, double fromY, double fromZ,
                                  double toX, double toY, double toZ,
                                  int cellX, int cellY, int cellZ) {
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        for (int offset = 0; offset < boxes.length; offset += 6) {
            double near = SEGMENT_EPSILON;
            double far = 1.0 - SEGMENT_EPSILON;
            for (int axis = 0; axis < 3; axis++) {
                double origin = axis == 0 ? fromX : axis == 1 ? fromY : fromZ;
                double direction = axis == 0 ? dx : axis == 1 ? dy : dz;
                double cell = axis == 0 ? cellX : axis == 1 ? cellY : cellZ;
                double minimum = cell + boxes[offset + axis];
                double maximum = cell + boxes[offset + 3 + axis];
                if (Math.abs(direction) < 1.0e-20) {
                    if (origin < minimum || origin > maximum) {
                        near = 1;
                        far = 0;
                        break;
                    }
                } else {
                    double first = (minimum - origin) / direction;
                    double second = (maximum - origin) / direction;
                    if (first > second) {
                        double swap = first;
                        first = second;
                        second = swap;
                    }
                    near = Math.max(near, first);
                    far = Math.min(far, second);
                    if (near > far) break;
                }
            }
            if (near <= far) return true;
        }
        return false;
    }

    private int offset(int box) {
        if (box < 0 || box >= boxCount()) throw new IndexOutOfBoundsException(box);
        return box * 6;
    }
}
