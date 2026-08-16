package com.mine.geometry_node.client.model.render.backend.common;

import com.mine.geometry_node.client.model.runtime.ModelInstanceBounds;
import com.mine.geometry_node.client.model.runtime.ModelInstancePlacement;
import com.mine.geometry_node.client.model.runtime.ModelWorldBounds;
import com.mine.geometry_node.core.engine.system.model.domain.ModelBounds;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Matrix4fc;
import org.joml.Vector3d;
import org.joml.Vector3f;

/** Spatial conversion shared by NATIVE rendering profiles. */
public final class ModelRenderBounds {
    private ModelRenderBounds() {}

    public static AABB worldBounds(ModelBounds bounds, ModelInstancePlacement placement) {
        Vector3d origin = placement.position();
        Quaternionf rotation = placement.rotation();
        Vector3f scale = placement.scale();
        ModelWorldBounds transformed = ModelInstanceBounds.transform(bounds, origin.x, origin.y, origin.z,
                rotation.x, rotation.y, rotation.z, rotation.w, scale.x, scale.y, scale.z);
        return new AABB(transformed.minX(), transformed.minY(), transformed.minZ(),
                transformed.maxX(), transformed.maxY(), transformed.maxZ());
    }

    /** Transforms primitive-local bounds through its node pose and then the instance placement. */
    public static AABB worldBounds(ModelBounds bounds, Matrix4fc nodeWorld, ModelInstancePlacement placement) {
        Vector3d origin = placement.position();
        ModelWorldBounds transformed = ModelInstanceBounds.transform(bounds, nodeWorld,
                origin.x, origin.y, origin.z, placement.rotation(), placement.scale());
        return new AABB(transformed.minX(), transformed.minY(), transformed.minZ(),
                transformed.maxX(), transformed.maxY(), transformed.maxZ());
    }
}
