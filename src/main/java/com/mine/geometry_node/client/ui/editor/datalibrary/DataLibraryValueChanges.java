package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.client.datalibrary.DataLibraryRepository;

import com.mine.geometry_node.core.engine.system.datalibrary.model.DataLibraryValueCodec;
import net.minecraft.client.Minecraft;

/**
 * Pure value mutation helpers shared by card editors.
 *
 * Keeping equality and replacement construction here prevents each renderer
 * (text, item, entity and block cards) from implementing subtly different
 * persistence rules.  The returned record is immutable and can safely be
 * handed to the repository after a picker or overlay closes.
 */
final class DataLibraryValueChanges {
    private DataLibraryValueChanges() {
    }

    static DataLibraryRepository.Entry replace(DataLibraryRepository.Entry current, Object value) {
        if (current == null) throw new IllegalArgumentException("current entry is required");
        return new DataLibraryRepository.Entry(current.id(), current.parentId(), current.type(),
                current.key(), value, current.tags(), current.data());
    }

    static boolean equivalent(DataLibraryRepository.Entry current, Object candidate) {
        if (current == null) return candidate == null;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return java.util.Objects.equals(current.value(), candidate);
        try {
            var registries = minecraft.level.registryAccess();
            return DataLibraryValueCodec.encode(current.type(), current.value(), registries)
                    .equals(DataLibraryValueCodec.encode(current.type(), candidate, registries));
        } catch (RuntimeException ignored) {
            // Unsupported values are still sent to the authoritative side,
            // which provides the canonical validation/error response.
            return java.util.Objects.equals(current.value(), candidate);
        }
    }

    static String serialize(DataLibraryRepository.Entry entry) {
        if (entry == null) return "";
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) return String.valueOf(entry.value());
        try {
            return DataLibraryValueCodec.encode(entry.type(), entry.value(),
                    minecraft.level.registryAccess()).toString();
        } catch (RuntimeException ignored) {
            return String.valueOf(entry.value());
        }
    }
}
