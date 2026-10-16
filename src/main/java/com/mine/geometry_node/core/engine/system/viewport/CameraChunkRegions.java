package com.mine.geometry_node.core.engine.system.viewport;

import java.util.List;
import java.util.function.LongConsumer;

/** Bounded region union: never loads the rectangle between remote observers. */
public record CameraChunkRegions(Region player, List<Region> cameras) {
    public CameraChunkRegions {
        EditCameraObservers.checkCount(cameras.size());
        cameras = List.copyOf(cameras).stream().distinct().toList();
    }
    public static final CameraChunkRegions EMPTY = new CameraChunkRegions(null, List.of());
    public boolean contains(int x, int z, boolean neighbors) {
        if (player != null && player.contains(x, z, neighbors)) return true;
        for (Region camera : cameras) if (camera.contains(x, z, neighbors)) return true;
        return false;
    }
    public boolean contains(long pos) { return contains((int) pos, (int) (pos >> 32), true); }
    public void forEach(LongConsumer consumer) {
        if (player != null) player.forEach(consumer);
        for (int i = 0; i < cameras.size(); i++) {
            int previousCount = i;
            cameras.get(i).forEach(pos -> {
                if (player != null && player.contains(pos)) return;
                for (int j = 0; j < previousCount; j++) if (cameras.get(j).contains(pos)) return;
                consumer.accept(pos);
            });
        }
    }
    public static void difference(CameraChunkRegions from, CameraChunkRegions to, LongConsumer enter, LongConsumer leave) {
        if (from.equals(to)) return;
        from.forEach(pos -> { if (!to.contains(pos)) leave.accept(pos); });
        to.forEach(pos -> { if (!from.contains(pos)) enter.accept(pos); });
    }
    public record Region(int x, int z, int radius) {
        public Region {
            if (radius < 2 || radius > 32) throw new IllegalArgumentException("Chunk radius must be 2..32");
            if (Math.abs((long) x) > 1_875_000 || Math.abs((long) z) > 1_875_000)
                throw new IllegalArgumentException("Chunk center outside world bounds");
        }
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
