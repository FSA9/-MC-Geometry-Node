package com.mine.geometry_node.mixin.entityappearance;

import com.mine.geometry_node.client.runtime.entity.appearance.ClientEntityAppearances;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearances;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityAppearanceDispatcherMixin {
    @Inject(method = "extractEntity", at = @At("HEAD"), cancellable = true)
    private void geometryNode$extractAppearance(Entity entity, float partialTick,
            CallbackInfoReturnable<EntityRenderState> cir) {
        var appearance = ClientEntityAppearances.resolve(entity, partialTick);
        if (appearance == null) return;
        cir.setReturnValue(appearance.state());
    }

    @Inject(method = "getRenderer(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",
            at = @At("HEAD"), cancellable = true)
    private void geometryNode$matchedAppearanceRenderer(EntityRenderState state,
            CallbackInfoReturnable<EntityRenderer<?, ?>> cir) {
        var renderer = ClientEntityAppearances.rendererFor(state);
        if (renderer != null) cir.setReturnValue(renderer);
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private void geometryNode$cullAppearance(Entity entity, Frustum frustum, double camX, double camY, double camZ,
            CallbackInfoReturnable<Boolean> cir) {
        if (EntityAppearances.get(entity).isNone()) return;
        // Runtime owns native fallback, proxy recursion protection and visual bounds.
        cir.setReturnValue(ClientEntityAppearances.shouldRender(entity, frustum, camX, camY, camZ));
    }
}
