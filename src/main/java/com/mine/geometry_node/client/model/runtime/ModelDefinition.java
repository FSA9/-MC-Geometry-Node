package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import com.mine.geometry_node.core.engine.system.model.collision.ModelCollisionSettings;
import com.mine.geometry_node.core.engine.system.model.math.ModelTransform;
import com.mine.geometry_node.core.engine.system.model.runtime.ModelInstanceLifetime;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Session-only recipe and small CPU metadata: source, settings, scene bounds, and material overrides.
 * Never retains imported mesh arrays, decoded textures, prepared render/collision data, or a lease.
 * Restoration reacquires the source; this is not a retained full CPU template or persistent storage.
 */
final class ModelDefinition {
    final String id;
    final ClientModelService.Source source;
    final ModelInstanceLifetime lifetime;
    final ModelActivation activation = new ModelActivation();
    final Map<Integer, Map<String, Float>> materialOverrides;
    ModelTransform transform;
    int scene;
    boolean visible;
    ModelCollisionSettings collision;
    List<GltfAsset.Bounds> sceneBounds = List.of();
    long revision;
    boolean dormant;

    ModelDefinition(String id, ClientModelService.Source source, ModelTransform transform, int scene,
                    boolean visible, ModelInstanceLifetime lifetime, ModelCollisionSettings collision,
                    Map<Integer, Map<String, Float>> materialOverrides) {
        this.id = id; this.source = source; this.transform = transform; this.scene = scene;
        this.visible = visible; this.lifetime = lifetime; this.collision = collision;
        this.materialOverrides = materialOverrides;
    }

    AABB worldBounds() {
        return sceneBounds.isEmpty() ? null : ModelResidencyBounds.transform(sceneBounds.get(scene), transform);
    }

    ModelActivation.Ticket beginActivation() {
        dormant=false;
        return activation.begin();
    }

    void suspend() {
        activation.invalidate();
        dormant=true;
    }

    ClientModelService.DefinitionSnapshot snapshot() {
        Map<Integer, Map<String, Float>> overrides = new LinkedHashMap<>();
        materialOverrides.forEach((index, values) -> overrides.put(index, Map.copyOf(values)));
        return new ClientModelService.DefinitionSnapshot(id, source, scene, transform, visible, lifetime,
                collision, revision, dormant, Map.copyOf(overrides));
    }
}
