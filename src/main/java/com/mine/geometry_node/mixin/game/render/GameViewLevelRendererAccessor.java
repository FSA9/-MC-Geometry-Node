package com.mine.geometry_node.mixin.game.render;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.gizmos.SimpleGizmoCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LevelRenderer.class)
public interface GameViewLevelRendererAccessor {
    @Accessor("collectedGizmos")
    SimpleGizmoCollector geometryNode$getCollectedGizmos();
}
