package com.mine.geometry_node.client.ui.components.integration.equipment;

import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.Map;

/** Codec-only transformation; the source appearance and every draft stack remain detached. */
final class EquipmentPreviewTemplates {
    private EquipmentPreviewTemplates() {}

    static EntityTemplateValue apply(EntityTemplateValue source, Map<String, ItemStack> draft,
                                     HolderLookup.Provider registries) {
        if (source.isEmpty()) return source;
        CompoundTag data = source.data();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        EntityEquipment equipment = data.contains("equipment")
                ? EntityEquipment.CODEC.parse(ops, data.get("equipment")).getOrThrow(IllegalArgumentException::new)
                : new EntityEquipment();
        for (var entry : draft.entrySet()) {
            if (!EntityEquipmentDraft.EQUIPMENT_SLOTS.contains(entry.getKey()))
                throw new IllegalArgumentException("Unsupported draft equipment slot");
            equipment.set(EquipmentSlot.byName(entry.getKey()), entry.getValue().copy());
        }
        data.put("equipment", EntityEquipment.CODEC.encodeStart(ops, equipment).getOrThrow(IllegalArgumentException::new));
        return new EntityTemplateValue(source.entityTypeId(), data);
    }
}
