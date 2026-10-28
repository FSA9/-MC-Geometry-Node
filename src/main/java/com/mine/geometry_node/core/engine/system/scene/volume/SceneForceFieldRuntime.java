package com.mine.geometry_node.core.engine.system.scene.volume;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldAddress;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.forceField.ForceFieldResourceStore;
import com.mine.geometry_node.core.engine.system.scene.SceneDocument;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Private support areas and fields are disposable derivatives of one persisted scene object. */
final class SceneForceFieldRuntime {
    private record State(SceneObject object, UUID incarnation) {}
    private final Map<UUID, State> fields = new HashMap<>();

    static boolean isForceField(SceneObject object) { return object.typeId().startsWith("force_field/"); }
    static AreaAddress areaAddress(ResourceKey<Level> dimension, UUID id) {
        return new AreaAddress(dimension, "scene/force_field/" + id);
    }
    static ForceFieldAddress fieldAddress(ResourceKey<Level> dimension, UUID id) {
        return new ForceFieldAddress(dimension, "scene/" + id);
    }

    void synchronize(ServerLevel level, SceneDocument document, List<SceneObject> objects) {
        synchronize(level.getServer(), level.dimension(), level.getGameTime(), document, objects);
    }

    void synchronize(MinecraftServer server, ResourceKey<Level> dimension, long gameTime,
            SceneDocument document, List<SceneObject> objects) {
        var removed = new HashSet<>(fields.keySet());
        for (SceneObject object : objects) {
            if (!isForceField(object)) continue;
            removed.remove(object.id());
            UUID incarnation = Objects.requireNonNull(document.incarnation(object.id()), "Force field no longer exists");
            State previous = fields.get(object.id());
            boolean sameLifetime = previous != null && previous.incarnation().equals(incarnation);
            var areaAddress = areaAddress(dimension, object.id());
            var fieldAddress = fieldAddress(dimension, object.id());
            if (!object.enabled()) {
                if (!sameLifetime || previous.object().enabled()) removeDerived(server, dimension, object.id());
            } else {
                var area = AreaResourceStore.INSTANCE.get(server, areaAddress);
                var field = ForceFieldResourceStore.INSTANCE.get(server, fieldAddress);
                if (sameLifetime && object.equals(previous.object()) && area != null && field != null) continue;
                if (!sameLifetime || area == null || !previous.object().enabled()
                        || !SceneVolumeRuntime.resolved(previous.object()).equals(SceneVolumeRuntime.resolved(object))) {
                    AreaResourceStore.INSTANCE.putAuthored(server,
                            SceneVolumeRuntime.authoredResource(areaAddress, document, object, gameTime));
                }
                if (!sameLifetime || field == null || !previous.object().enabled()
                        || previous.object().number("strength") != object.number("strength")) {
                    ForceFieldResourceStore.INSTANCE.putAuthored(server, ForceFieldResource.authored(
                            fieldAddress, object.id(), object.revision(), areaAddress, gameTime,
                            (float) object.number("strength")));
                }
            }
            fields.put(object.id(), new State(object, incarnation));
        }
        for (UUID id : removed) { removeDerived(server, dimension, id); fields.remove(id); }
    }

    private void removeDerived(MinecraftServer server, ResourceKey<Level> dimension, UUID id) {
        ForceFieldResourceStore.INSTANCE.removeAuthored(server, fieldAddress(dimension, id));
        AreaResourceStore.INSTANCE.removeAuthored(server, areaAddress(dimension, id));
    }

    void clear(ServerLevel level) {
        clear(level.getServer(), level.dimension());
    }

    void clear(MinecraftServer server, ResourceKey<Level> dimension) {
        for (UUID id : fields.keySet()) removeDerived(server, dimension, id);
        fields.clear();
    }
}
