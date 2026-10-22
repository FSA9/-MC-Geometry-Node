package com.mine.geometry_node.core.engine.system.entityeditor;

import net.minecraft.world.entity.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Extensions may provide entity capabilities without adding registry-id checks to the editor. All calls use the server thread.
 * Validation must not mutate. Attribute writes must be reversible by writing the previous values; equipment writes defer hooks.
 */
public interface EntityEditorAdapter {
    boolean supports(Entity entity);
    Map<String, Object> attributes(Entity entity);
    void validateAttributes(Entity entity, Map<String, Object> changes);
    void applyAttributes(Entity entity, Map<String, Object> changes);
    List<EquipmentSlot> equipmentSlots(Entity entity);
    boolean acceptsEquipment(Entity entity, EquipmentSlot slot, ItemStack stack);
    default boolean supportsBehaviorTrees(Entity entity) { return entity instanceof Mob; }
}
