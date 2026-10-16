package com.mine.geometry_node.mixin.game.render;

import com.mine.geometry_node.client.runtime.game.EditViewportRenderContext;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Bridge notifications retained by ClientLevel and the resource reload manager. */
@Mixin(LevelRenderer.class)
public abstract class EditViewportLevelUpdatesMixin {
    // Sodium replaces the range methods directly rather than calling vanilla's
    // per-section helper. Wrap public entry points and suppress nested fan-out.
    @WrapMethod(method = "blockChanged")
    private void geometryNode$blockChanged(BlockGetter level, BlockPos pos, BlockState oldState,
            BlockState newState, int flags, Operation<Void> original) {
        EditViewportRenderContext.mutate((LevelRenderer) (Object) this,
                () -> original.call(level, pos, oldState, newState, flags),
                renderer -> renderer.blockChanged(level, pos, oldState, newState, flags));
    }

    @WrapMethod(method = "setBlocksDirty")
    private void geometryNode$blocksDirty(int x0, int y0, int z0, int x1, int y1, int z1, Operation<Void> original) {
        EditViewportRenderContext.mutate((LevelRenderer) (Object) this,
                () -> original.call(x0, y0, z0, x1, y1, z1),
                renderer -> renderer.setBlocksDirty(x0, y0, z0, x1, y1, z1));
    }

    @WrapMethod(method = "setSectionDirtyWithNeighbors")
    private void geometryNode$neighbors(int x, int y, int z, Operation<Void> original) {
        EditViewportRenderContext.mutate((LevelRenderer) (Object) this,
                () -> original.call(x, y, z), renderer -> renderer.setSectionDirtyWithNeighbors(x, y, z));
    }

    @WrapMethod(method = "setSectionRangeDirty")
    private void geometryNode$sectionRange(int x0, int y0, int z0, int x1, int y1, int z1, Operation<Void> original) {
        EditViewportRenderContext.mutate((LevelRenderer) (Object) this,
                () -> original.call(x0, y0, z0, x1, y1, z1),
                renderer -> renderer.setSectionRangeDirty(x0, y0, z0, x1, y1, z1));
    }

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void geometryNode$dirty(int x, int y, int z, boolean playerChanged, CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this,
                renderer -> ((EditViewportLevelStateAccessor) renderer).geometryNode$markSectionDirty(x, y, z, playerChanged));
    }

    @Inject(method = "onSectionBecomingNonEmpty", at = @At("HEAD"))
    private void geometryNode$nonEmpty(long section, CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, renderer -> renderer.onSectionBecomingNonEmpty(section));
    }

    @Inject(method = "onChunkReadyToRender", at = @At("HEAD"))
    private void geometryNode$chunkReady(ChunkPos pos, CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, renderer -> renderer.onChunkReadyToRender(pos));
    }

    @Inject(method = "destroyBlockProgress", at = @At("HEAD"))
    private void geometryNode$breaking(int id, BlockPos pos, int progress, CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, renderer -> renderer.destroyBlockProgress(id, pos, progress));
    }

    @Inject(method = "allChanged", at = @At("RETURN"))
    private void geometryNode$rebuild(CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, LevelRenderer::allChanged);
    }

    @Inject(method = "onResourceManagerReload", at = @At("RETURN"))
    private void geometryNode$reload(ResourceManager resources, CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, renderer -> renderer.onResourceManagerReload(resources));
    }

    @Inject(method = "resize", at = @At("RETURN"))
    private void geometryNode$resize(int width, int height, CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, renderer -> renderer.resize(width, height));
    }

    @Inject(method = "resetSampler", at = @At("HEAD"))
    private void geometryNode$sampler(CallbackInfo ci) {
        EditViewportRenderContext.forward((LevelRenderer) (Object) this, LevelRenderer::resetSampler);
    }

    @Inject(method = "endFrame", at = @At("TAIL"))
    private void geometryNode$endFrame(CallbackInfo ci) {
        EditViewportRenderContext.endFrame((LevelRenderer) (Object) this);
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void geometryNode$level(ClientLevel level, CallbackInfo ci) {
        EditViewportRenderContext.closeFor((LevelRenderer) (Object) this);
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void geometryNode$close(CallbackInfo ci) {
        EditViewportRenderContext.closeFor((LevelRenderer) (Object) this);
    }
}
