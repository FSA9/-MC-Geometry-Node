package com.mine.geometry_node.core.engine.system.scene;

import com.mine.geometry_node.core.engine.metadata.state.OwnerMetadata;
import com.mine.geometry_node.core.engine.metadata.value.GraphMetadataValuePolicy;

import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.core.engine.system.scene.SceneSavedData;
import com.mine.geometry_node.core.engine.system.scene.ServerSceneService;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import java.util.*;
import java.util.function.*;

/** Context-bearing authored owner handles, independent of snapshots and query rows. */
public final class SceneMetadataTargets {
    public static final String KIND = "geometry_node:scene_object";
    private SceneMetadataTargets() {}

    public static void register(MetadataCapabilities registry) {
        registry.register(KIND, Target.class, EnumSet.allOf(MetadataCapabilities.Capability.class), SceneMetadataTargets::resolve);
    }

    public static Target target(MinecraftServer server, String dimension, UUID id) {
        Runnable check = () -> {
            if (!server.isSameThread() || server.isStopped()) throw new IllegalStateException("Scene metadata requires the live owner server thread");
        };
        check.run();
        return new Target(SceneSavedData.get(server), dimension, id, server.registryAccess(), check,
                () -> ServerSceneService.metadataChanged(server, dimension));
    }
    public static MetadataCapabilities.Access access(MinecraftServer server, String dimension, UUID id) {
        return MetadataCapabilities.GLOBAL.resolve(KIND, target(server, dimension, id));
    }
    public static List<String> tags(MinecraftServer server, String dimension, UUID id) {
        return target(server, dimension, id).current().tags();
    }

    public static final class Target {
        private final SceneSavedData owner;
        private final SceneDocument document;
        private final String dimension, type;
        private final UUID id, incarnation;
        private final HolderLookup.Provider registries;
        private final Runnable check, changed;

        Target(SceneSavedData owner, String dimension, UUID id, HolderLookup.Provider registries, Runnable check, Runnable changed) {
            check.run();
            this.owner = owner; this.dimension = dimension; this.id = id; this.registries = registries;
            this.check = check; this.changed = changed;
            document = owner.document(dimension);
            SceneObject object = document.object(id);
            if (object == null) throw new IllegalStateException("Scene object no longer exists");
            if (SceneObjectTypes.require(object.typeId()).storage() != SceneObjectTypes.Storage.AUTHORED)
                throw new IllegalArgumentException("Native metadata belongs to the entity owner");
            type = object.typeId(); incarnation = document.incarnation(id);
        }
        private SceneObject current() {
            check.run();
            SceneObject object = document.object(id);
            if (owner.document(dimension) != document || !Objects.equals(incarnation, document.incarnation(id))
                    || object == null || !type.equals(object.typeId())) throw new IllegalStateException("Stale scene metadata target");
            return object;
        }
        private <V> MetadataStore<V> store(Function<SceneObject, V> read, BiFunction<SceneObject, V, OwnerMetadata> replace) {
            return new MetadataStore<>() {
                public V read() { return read.apply(current()); }
                public boolean update(UnaryOperator<V> transformation) {
                    current();
                    boolean committed = owner.updateMetadata(dimension, id, incarnation, ignored -> {
                        SceneObject before = current();
                        return replace.apply(before, transformation.apply(read.apply(before)));
                    });
                    if (committed) changed.run();
                    return committed;
                }
            };
        }
    }

    private static MetadataCapabilities.Access resolve(Target target) {
        target.current();
        return new MetadataCapabilities.Access(
                new TagAccess(target.store(SceneObject::tags, (object, tags) -> object.metadata().withTags(tags)), TagPolicy.EXACT),
                new DataAccess(target.store(object -> object.data(target.registries),
                        (object, data) -> object.metadata().withData(data, target.registries)), GraphMetadataValuePolicy.of(target.registries)));
    }
}
