package com.mine.geometry_node.mixin.entitypreview;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.ValueOutput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Dispatches directly to the entity's vanilla fields, bypassing attachments and passenger saves. */
@Mixin(Entity.class)
public interface EntityAppearanceInvoker {
    @Invoker("addAdditionalSaveData")
    void geometryNode$saveAppearanceFields(ValueOutput output);
}
