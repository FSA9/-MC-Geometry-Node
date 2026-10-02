package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.ModelPlacedLightField;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
public abstract class ModelPlacedLightWorldMixin {
    @Inject(method="setBlocksDirty",at=@At("HEAD"))
    private void geometryNode$lightChanged(BlockPos pos, BlockState oldState, BlockState newState, CallbackInfo ci) {
        if (!ModelPlacedLightField.watching(this,pos)) return;
        ClientLevel world=(ClientLevel)(Object)this;
        int emission=Math.clamp(newState.getLightEmission(world,pos),0,15);
        if(oldState.getLightEmission(world,pos)!=emission
                || (oldState.getLightDampening()>=15)!=(newState.getLightDampening()>=15))
            ModelPlacedLightField.blockChanged(world,pos,emission|(newState.getLightDampening()>=15?16:0));
    }
    @Inject(method="onChunkLoaded",at=@At("HEAD"))
    private void geometryNode$lightChunkLoaded(ChunkPos pos, CallbackInfo ci) {
        ModelPlacedLightField.chunkChanged(this,pos.x(),pos.z());
    }
    @Inject(method="unload",at=@At("HEAD"))
    private void geometryNode$lightChunkUnloaded(LevelChunk chunk, CallbackInfo ci) {
        ModelPlacedLightField.chunkChanged(this,chunk.getPos().x(),chunk.getPos().z());
    }
}
