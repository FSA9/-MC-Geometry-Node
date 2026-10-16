package com.mine.geometry_node.mixin.game.compat.sodium;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRenderContext;
import com.mine.geometry_node.client.runtime.game.chunk.ViewportChunkReadiness;
import it.unimi.dsi.fastutil.longs.LongCollection;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Sodium's ClientLevel event queue is destructive: independent terrain managers
 * compare their own ready sets, leaving the player's original event consumer intact. */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer", remap = false)
public abstract class EditViewportSodiumChunksMixin {
    @Unique private boolean geometryNode$editor;
    @Unique private ViewportChunkReadiness geometryNode$ready;
    @Unique private Field geometryNode$managerField;
    @Unique private Field geometryNode$levelField;
    @Unique private Method geometryNode$getTracker;
    @Unique private Method geometryNode$getReady;
    @Unique private Method geometryNode$beforeUpdates;
    @Unique private Method geometryNode$addChunk;
    @Unique private Method geometryNode$removeChunk;

    @Inject(method = "initRenderer", at = @At("RETURN"), remap = false)
    private void geometryNode$initializeReadySet(CallbackInfo ci) {
        geometryNode$editor = EditViewportRenderContext.isEditorRenderer(Minecraft.getInstance().levelRenderer);
        if (!geometryNode$editor) return;
        try {
            if (geometryNode$managerField == null) {
                Class<?> type = getClass();
                geometryNode$managerField = type.getDeclaredField("renderSectionManager");
                geometryNode$levelField = type.getDeclaredField("level");
                geometryNode$managerField.setAccessible(true);
                geometryNode$levelField.setAccessible(true);
                Class<?> holder = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTrackerHolder");
                geometryNode$getTracker = holder.getMethod("get", net.minecraft.client.multiplayer.ClientLevel.class);
                Class<?> tracker = Class.forName("net.caffeinemc.mods.sodium.client.render.chunk.map.ChunkTracker");
                geometryNode$getReady = tracker.getMethod("getReadyChunks");
                Class<?> manager = geometryNode$managerField.getType();
                geometryNode$beforeUpdates = manager.getMethod("beforeSectionUpdates");
                geometryNode$addChunk = manager.getMethod("onChunkAdded", int.class, int.class);
                geometryNode$removeChunk = manager.getMethod("onChunkRemoved", int.class, int.class);
            }
            Object tracker = geometryNode$getTracker.invoke(null, geometryNode$levelField.get(this));
            geometryNode$ready = new ViewportChunkReadiness((LongCollection) geometryNode$getReady.invoke(tracker), tracker);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot initialize independent Sodium terrain state", failure);
        }
    }

    @Inject(method = "processChunkEvents", at = @At("HEAD"), cancellable = true, remap = false)
    private void geometryNode$updateReadySet(CallbackInfo ci) {
        if (!geometryNode$editor) return;
        try {
            Object manager = geometryNode$managerField.get(this);
            geometryNode$beforeUpdates.invoke(manager);
            geometryNode$ready.update(geometryNode$readyChunks(),
                    key -> geometryNode$notifyChunk(geometryNode$addChunk, manager, key),
                    key -> geometryNode$notifyChunk(geometryNode$removeChunk, manager, key));
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot update independent Sodium terrain state", failure);
        }
        ci.cancel();
    }

    @Unique private LongCollection geometryNode$readyChunks() throws ReflectiveOperationException {
        Object tracker = geometryNode$getTracker.invoke(null, geometryNode$levelField.get(this));
        return (LongCollection) geometryNode$getReady.invoke(tracker);
    }

    @Unique private static void geometryNode$notifyChunk(Method method, Object manager, long key) {
        try { method.invoke(manager, ChunkPos.getX(key), ChunkPos.getZ(key)); }
        catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot deliver independent Sodium chunk notification", failure);
        }
    }
}
