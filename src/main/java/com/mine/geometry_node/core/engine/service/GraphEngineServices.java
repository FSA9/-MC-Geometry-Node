package com.mine.geometry_node.core.engine.service;

import com.mine.geometry_node.core.engine.graph.runtime.GraphRuntimeContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Root placeholder for services shared by graph runtimes.
 * Concrete services should live here only when they are not blueprint,
 * dialogue, or behavior-tree specific.
 */
public final class GraphEngineServices {
    public static final GraphEngineServices INSTANCE = new GraphEngineServices();

    private VisualSink visualSink = VisualSink.NOOP;
    private PersistentAttributeStore persistentAttributeStore = PersistentAttributeStore.NOOP;

    private GraphEngineServices() {
    }

    public VisualSink visualSink() {
        return visualSink;
    }

    public void setVisualSink(@Nullable VisualSink visualSink) {
        this.visualSink = visualSink != null ? visualSink : VisualSink.NOOP;
    }

    public PersistentAttributeStore persistentAttributes() {
        return persistentAttributeStore;
    }

    public void setPersistentAttributeStore(@Nullable PersistentAttributeStore persistentAttributeStore) {
        this.persistentAttributeStore = persistentAttributeStore != null
                ? persistentAttributeStore
                : PersistentAttributeStore.NOOP;
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
            double radius
    ) {
    }

    public interface PersistentAttributeStore {
        PersistentAttributeStore NOOP = new PersistentAttributeStore() {
            @Override
            public void set(@Nullable GraphRuntimeContext context, @Nullable PersistentAttributeTarget target, String name, @Nullable Object value) {
            }

            @Override
            public @Nullable Object get(@Nullable GraphRuntimeContext context, @Nullable PersistentAttributeTarget target, String name) {
                return null;
            }
        };

        void set(@Nullable GraphRuntimeContext context, @Nullable PersistentAttributeTarget target, String name, @Nullable Object value);

        @Nullable
        Object get(@Nullable GraphRuntimeContext context, @Nullable PersistentAttributeTarget target, String name);

        @Deprecated
        default void set(@Nullable GraphRuntimeContext context, @Nullable Object target, String name, @Nullable Object value) {
            PersistentAttributeTarget converted = convertLegacyTarget(target);
            if (converted != null) {
                set(context, converted, name, value);
            }
        }

        @Deprecated
        @Nullable
        default Object get(@Nullable GraphRuntimeContext context, @Nullable Object target, String name) {
            PersistentAttributeTarget converted = convertLegacyTarget(target);
            return converted != null ? get(context, converted, name) : null;
        }

        private static PersistentAttributeTarget convertLegacyTarget(@Nullable Object target) {
            if (target == null) {
                return PersistentAttributeTarget.global();
            }
            if (target instanceof PersistentAttributeTarget typedTarget) {
                return typedTarget;
            }
            if (target instanceof Entity entity) {
                return PersistentAttributeTarget.entity(entity);
            }
            if (target instanceof String scopeId && !scopeId.isBlank()) {
                return "GLOBAL".equals(scopeId)
                        ? PersistentAttributeTarget.global()
                        : PersistentAttributeTarget.scope(scopeId);
            }
            return null;
        }
    }
}
