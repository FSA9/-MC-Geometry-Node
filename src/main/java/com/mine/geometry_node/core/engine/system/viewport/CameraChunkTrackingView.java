package com.mine.geometry_node.core.engine.system.viewport;

import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.world.level.ChunkPos;
import java.util.function.Consumer;

/** Two bounded view regions; never loads the rectangle between player and camera. */
public record CameraChunkTrackingView(CameraChunkRegions regions) implements ChunkTrackingView {
    public CameraChunkTrackingView(ChunkTrackingView.Positioned player, ChunkTrackingView.Positioned camera) {
        this(new CameraChunkRegions(region(player), region(camera)));
    }
    @Override public boolean contains(int x, int z, boolean neighbors) {
        return regions.contains(x, z, neighbors);
    }
    @Override public void forEach(Consumer<ChunkPos> consumer) {
        regions.forEach(pos -> consumer.accept(new ChunkPos((int) pos, (int) (pos >> 32))));
    }
    public static void difference(ChunkTrackingView from, ChunkTrackingView to,
                                  Consumer<ChunkPos> enter, Consumer<ChunkPos> leave) {
        if (from.equals(to)) return;
        CameraChunkRegions source = regions(from), destination = regions(to);
        if (source != null && destination != null) {
            CameraChunkRegions.difference(source, destination,
                    pos -> enter.accept(new ChunkPos((int) pos, (int) (pos >> 32))), pos -> leave.accept(new ChunkPos((int) pos, (int) (pos >> 32))));
            return;
        }
        // Other mods may supply another view implementation.
        from.forEach(pos -> { if (!to.contains(pos)) leave.accept(pos); });
        to.forEach(pos -> { if (!from.contains(pos)) enter.accept(pos); });
    }
    private static CameraChunkRegions regions(ChunkTrackingView view) {
        if (view == ChunkTrackingView.EMPTY) return CameraChunkRegions.EMPTY;
        if (view instanceof CameraChunkTrackingView union) return union.regions;
        if (view instanceof ChunkTrackingView.Positioned positioned) return new CameraChunkRegions(region(positioned), null);
        return null;
    }
    private static CameraChunkRegions.Region region(ChunkTrackingView.Positioned view) {
        return new CameraChunkRegions.Region(view.center().x(), view.center().z(), view.viewDistance());
    }
}
