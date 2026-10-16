package com.mine.geometry_node.core.engine.system.viewport;

import java.util.function.LongConsumer;

/** Registry-independent region algebra used by the server tracking adapter. */
public record CameraChunkRegions(Region player, Region camera) {
    public static final CameraChunkRegions EMPTY = new CameraChunkRegions(null, null);
    public boolean contains(int x, int z, boolean neighbors) {
        return (player != null && player.contains(x, z, neighbors)) || (camera != null && camera.contains(x, z, neighbors));
    }
    public boolean contains(long pos) { return contains((int) pos, (int) (pos >> 32), true); }
    public void forEach(LongConsumer consumer) {
        if (player != null) player.forEach(consumer);
        if (camera != null) camera.forEach(pos -> { if (player == null || !player.contains(pos)) consumer.accept(pos); });
    }
    public static void difference(CameraChunkRegions from, CameraChunkRegions to, LongConsumer enter, LongConsumer leave) {
        if (from.equals(to)) return;
        from.forEach(pos -> { if (!to.contains(pos)) leave.accept(pos); });
        to.forEach(pos -> { if (!from.contains(pos)) enter.accept(pos); });
    }
    public record Region(int x, int z, int radius) {
        public Region { if (radius < 2 || radius > 32) throw new IllegalArgumentException("Chunk radius must be 2..32"); }
        public boolean contains(long pos) { return contains((int) pos, (int) (pos >> 32), true); }
        public boolean contains(int cx, int cz, boolean neighbors) {
            int padding = neighbors ? 2 : 1;
            long dx = Math.max(0, Math.abs((long) cx - x) - padding);
            long dz = Math.max(0, Math.abs((long) cz - z) - padding);
            return dx * dx + dz * dz < (long) radius * radius;
        }
        public void forEach(LongConsumer consumer) {
            for (int cx = x - radius - 1; cx <= x + radius + 1; cx++) {
                for (int cz = z - radius - 1; cz <= z + radius + 1; cz++) {
                    if (contains(cx, cz, true)) consumer.accept((cx & 0xffffffffL) | ((long) cz << 32));
                }
            }
        }
    }
}
