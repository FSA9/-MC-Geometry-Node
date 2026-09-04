package com.mine.geometry_node.core.node.definition.port;

import com.mine.geometry_node.core.node.value.DialogueChoiceValue;
import com.mine.geometry_node.core.node.value.QuestConditionValue;
import com.mine.geometry_node.core.node.value.RichTextValue;
import com.mine.geometry_node.core.node.value.SlotRef;
import com.mine.geometry_node.core.node.value.color.ColorValue;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Single source of truth for mapping Java value classes to graph port types.
 * Semantic aliases that share a Java class (for example STRING/PATH) remain a
 * schema concern and deliberately cannot be inferred from a runtime value.
 */
public final class PortValueTypeRegistry {
    private static final Map<Class<?>, PortType> EXACT_TYPES = createExactTypes();
    private static final Set<Class<?>> SOURCE_ONLY_TYPES = Set.of(
            Byte.class, Short.class, UUID.class, BlockPos.class);

    private PortValueTypeRegistry() {
    }

    public static PortType infer(@Nullable Object value) {
        if (value == null) return PortType.ANY;
        PortType exact = EXACT_TYPES.get(value.getClass());
        if (exact != null) return exact;
        if (value instanceof Number) return PortType.FLOAT;
        if (value instanceof Entity) return PortType.ENTITY;
        if (value instanceof Item) return PortType.ITEM;
        if (value instanceof List<?>) return PortType.LIST;
        if (value instanceof Map<?, ?>) return PortType.DICT;
        return PortType.ANY;
    }

    /**
     * Resolves the graph type used to adapt a value to a requested Java API
     * type. Returns null when the Java type has no graph representation.
     */
    public static @Nullable PortType forJavaClass(@Nullable Class<?> javaType) {
        if (javaType == null) return null;
        // These classes are accepted source representations, but graph ports
        // canonicalize them to Integer, Entity, or Vec3 respectively.
        if (SOURCE_ONLY_TYPES.contains(javaType)) return null;
        PortType exact = EXACT_TYPES.get(javaType);
        if (exact != null) return exact;
        if (Number.class.isAssignableFrom(javaType)) return PortType.FLOAT;
        if (Entity.class.isAssignableFrom(javaType)) return PortType.ENTITY;
        if (Item.class.isAssignableFrom(javaType)) return PortType.ITEM;
        if (List.class.isAssignableFrom(javaType)) return PortType.LIST;
        if (Map.class.isAssignableFrom(javaType)) return PortType.DICT;
        return null;
    }

    private static Map<Class<?>, PortType> createExactTypes() {
        Map<Class<?>, PortType> types = new LinkedHashMap<>();
        register(types, Integer.class, PortType.INTEGER);
        register(types, Byte.class, PortType.INTEGER);
        register(types, Short.class, PortType.INTEGER);
        register(types, Long.class, PortType.LONG);
        register(types, Float.class, PortType.FLOAT);
        register(types, Double.class, PortType.FLOAT);
        register(types, Boolean.class, PortType.BOOLEAN);
        register(types, String.class, PortType.STRING);
        register(types, RichTextValue.class, PortType.RICH_TEXT);
        register(types, ColorValue.class, PortType.COLOR);
        register(types, GeometryValue.class, PortType.GEOMETRY);
        register(types, SlotRef.class, PortType.SLOT);
        register(types, Entity.class, PortType.ENTITY);
        register(types, UUID.class, PortType.ENTITY);
        register(types, EntityTemplateValue.class, PortType.ENTITY_TEMPLATE);
        register(types, Item.class, PortType.ITEM);
        register(types, ItemStack.class, PortType.ITEM_STACK);
        register(types, BlockState.class, PortType.BLOCK);
        register(types, DialogueChoiceValue.class, PortType.DIALOGUE_CHOICE);
        register(types, QuestConditionValue.class, PortType.QUEST_CONDITION);
        register(types, Vec3.class, PortType.XYZ);
        register(types, BlockPos.class, PortType.XYZ);
        register(types, List.class, PortType.LIST);
        register(types, Map.class, PortType.DICT);
        return Map.copyOf(types);
    }

    private static void register(Map<Class<?>, PortType> types,
                                 Class<?> javaType, PortType portType) {
        PortType previous = types.putIfAbsent(javaType, portType);
        if (previous != null) {
            throw new IllegalStateException("Duplicate Java value type mapping: "
                    + javaType.getName());
        }
    }
}
