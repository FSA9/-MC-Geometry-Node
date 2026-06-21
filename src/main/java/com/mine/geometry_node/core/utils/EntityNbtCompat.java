package com.mine.geometry_node.core.utils;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

public final class EntityNbtCompat {
    private EntityNbtCompat() {
    }

    public static CompoundTag saveWithoutId(Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, entity.registryAccess());
        entity.saveWithoutId(output);
        return output.buildResult();
    }

    public static void load(Entity entity, CompoundTag tag) {
        HolderLookup.Provider provider = entity.registryAccess();
        entity.load(TagValueInput.create(ProblemReporter.DISCARDING, provider, tag));
    }
}
