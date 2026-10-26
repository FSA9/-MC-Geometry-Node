package com.mine.geometry_node.mixin.entityappearance;

import com.mine.geometry_node.client.runtime.entity.appearance.AppearanceRendererBounds;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Dispatches virtually so display entities and other custom renderers retain their visual bounds. */
@Mixin(EntityRenderer.class)
public abstract class EntityAppearanceRendererBoundsMixin<T extends Entity, S extends EntityRenderState>
        implements AppearanceRendererBounds {
    @Shadow
    protected abstract AABB getBoundingBoxForCulling(T entity);

    @Override
    @SuppressWarnings("unchecked")
    public AABB geometryNode$appearanceBounds(Entity entity) {
        return getBoundingBoxForCulling((T) entity);
    }
}
