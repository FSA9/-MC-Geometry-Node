package com.mine.geometry_node.client.ui.editor.datalibrary.library;

import com.mine.geometry_node.client.feature.datalibrary.DataLibraryRepository;
import com.mine.geometry_node.client.feature.datalibrary.DataLibraryEntryValues;

import com.mine.geometry_node.core.node.definition.port.PortType;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/** Read-only presentation of values already present in a Data Library snapshot. Never reads a world. */
public final class DataLibraryValuePresentation {
    private DataLibraryValuePresentation() {}

    /** Complete stored value for inspector/copy actions, independent of the browsing view. */
    public static String valueDetails(DataLibraryRepository.Entry entry) {
        if (entry == null) return "";
        return entry.value() instanceof String text ? text : DataLibraryEntryValues.serialize(entry);
    }

    /**
     * Returns a block, item, or entity registry ID, or empty when the value has no known registry type.
     * Block-state properties and stack counts do not affect the ID. Empty stacks have no ID.
     * Entity templates supply their saved entity type; UUID references remain empty in this overload.
     * This method never resolves a UUID or inspects a live entity.
     */
    public static String registryType(DataLibraryRepository.Entry entry) {
        if (entry == null || entry.value() == null) return "";
        Object value = entry.value();
        return switch (entry.type()) {
            case BLOCK_STATE -> value instanceof BlockState state
                    ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : "";
            case ITEM -> value instanceof Item item ? BuiltInRegistries.ITEM.getKey(item).toString() : "";
            case ITEM_STACK -> value instanceof ItemStack stack && !stack.isEmpty()
                    ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : "";
            case ENTITY_TEMPLATE -> EntityTemplateValue.from(value).entityTypeId();
            default -> "";
        };
    }

    /**
     * Uses an optional ID from the caller's asynchronously resolved UUID metadata snapshot.
     * Callers own its source/lifecycle association and must clear it when the reference changes or becomes unavailable.
     * Static values always use their own metadata, regardless of the supplied resolved ID.
     */
    public static String registryType(DataLibraryRepository.Entry entry, String resolvedEntityType) {
        return entry != null && entry.type() == PortType.ENTITY && entry.value() instanceof UUID
                ? resolvedEntityType == null ? "" : resolvedEntityType : registryType(entry);
    }

    /** Plain table value text; block states and items show only their registry ID, without state properties. */
    public static String tableValueText(DataLibraryRepository.Entry entry, String summary) {
        if (entry != null && (entry.type() == PortType.BLOCK_STATE || entry.type() == PortType.ITEM)) {
            return registryType(entry);
        }
        return summary == null ? "" : summary;
    }

    /**
     * Representative item appearance for block states and item values. Blocks without an item form
     * and empty/unsupported values produce an empty stack; no world or block renderer is consulted.
     * Existing stacks are borrowed read-only; the preview copies them only when their contents change.
     */
    public static ItemStack previewStack(DataLibraryRepository.Entry entry) {
        if (entry == null || entry.value() == null) return ItemStack.EMPTY;
        Object value = entry.value();
        return switch (entry.type()) {
            case BLOCK_STATE -> value instanceof BlockState state ? new ItemStack(state.getBlock()) : ItemStack.EMPTY;
            case ITEM -> value instanceof Item item ? new ItemStack(item) : ItemStack.EMPTY;
            case ITEM_STACK -> value instanceof ItemStack stack ? stack : ItemStack.EMPTY;
            default -> ItemStack.EMPTY;
        };
    }
}
