package com.mine.geometry_node.core.node.nodes.actions.entity;

import com.mine.geometry_node.core.utils.EntityNbtCompat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.storage.TagValueOutput;

public class SetEntityPersistence extends AbstractSetEntityBooleanProperty {
    public static final String TYPE_ID = "set_entity_persistence";

    public SetEntityPersistence() {
        super(TYPE_ID, true, (entity, value) -> {
            if (entity instanceof Mob mob) {
                if (value) {
                    mob.setPersistenceRequired();
                } else {
                    TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, mob.registryAccess());
                    if (mob.saveAsPassenger(output)) {
                        CompoundTag tag = output.buildResult();
                        tag.putBoolean("PersistenceRequired", false);
                        EntityNbtCompat.load(mob, tag);
                    }
                }
            }
        });
    }
}
