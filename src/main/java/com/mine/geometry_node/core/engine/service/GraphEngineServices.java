package com.mine.geometry_node.core.engine.service;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.List;

/**
 * Root placeholder for services shared by graph runtimes.
 * Concrete services should live here only when they are not blueprint,
 * dialogue, or behavior-tree specific.
 */
public final class GraphEngineServices {
    public static final GraphEngineServices INSTANCE = new GraphEngineServices();

    private VisualSink visualSink = VisualSink.NOOP;
    private ScopedStateStore scopedStateStore = ScopedStateStore.NOOP;

    private GraphEngineServices() {
    }

    public VisualSink visualSink() {
        return visualSink;
    }

    public void setVisualSink(@Nullable VisualSink visualSink) {
        this.visualSink = visualSink != null ? visualSink : VisualSink.NOOP;
    }

    public ScopedStateStore scopedState() {
        return scopedStateStore;
    }

    public void setScopedStateStore(@Nullable ScopedStateStore scopedStateStore) {
        this.scopedStateStore = scopedStateStore != null ? scopedStateStore : ScopedStateStore.NOOP;
    }

    public interface VisualSink {
        VisualSink NOOP = effect -> {
        };

        void broadcast(VisualEffect effect);
    }

    public record VisualEffect(
            ServerLevel level,
            String effectType,
            int color,
            int durationTicks,
            Map<String, String> expressions,
            Map<String, String> bindings,
            CompoundTag extraData,
            Vec3 center,
            double radius,
            List<VisualAsset> assets
    ) {
    }

    public record VisualAsset(String assetId, byte[] data) {
        public VisualAsset {
            if (assetId == null || assetId.isBlank()) {
                throw new IllegalArgumentException("assetId must not be blank");
            }
            data = data != null ? data : new byte[0];
        }
    }

}
