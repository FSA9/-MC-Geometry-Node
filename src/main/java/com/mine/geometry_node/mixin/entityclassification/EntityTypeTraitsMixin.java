package com.mine.geometry_node.mixin.entityclassification;

import com.mine.geometry_node.core.engine.system.entity.classification.EntityTypeTraitHolder;
import com.mine.geometry_node.core.engine.system.entity.classification.EntityTypeTraits;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** One instance-level boundary serves native tag checks, enchantment predicates and tag enumeration. */
@Mixin(Entity.class)
public abstract class EntityTypeTraitsMixin {
    @Unique private Holder<EntityType<?>> geometryNode$typeTraits;

    @Inject(method = "typeHolder", at = @At("RETURN"), cancellable = true)
    private void geometryNode$typeTraits(CallbackInfoReturnable<Holder<EntityType<?>>> callback) {
        Entity entity = (Entity) (Object) this;
        if (EntityTypeTraits.overrides(entity).values().isEmpty()) return;
        if (geometryNode$typeTraits == null) geometryNode$typeTraits = new EntityTypeTraitHolder(
                callback.getReturnValue(), () -> EntityTypeTraits.overrides(entity).values());
        callback.setReturnValue(geometryNode$typeTraits);
    }
}
