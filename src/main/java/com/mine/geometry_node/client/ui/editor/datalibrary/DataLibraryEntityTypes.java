package com.mine.geometry_node.client.ui.editor.datalibrary;

import com.mine.geometry_node.core.node.definition.port.PortType;
import icyllis.modernui.view.View;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.item.ItemEntity;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

/** Window-owned immutable UUID/type snapshots, independent of row mounting and native rendering. */
final class DataLibraryEntityTypes {
    private final Consumer<Runnable> gameTasks;
    private final Consumer<Runnable> uiTasks;
    private final Function<UUID, String> resolve;
    private volatile long generation;
    private Map<UUID, String> types = Map.of();

    DataLibraryEntityTypes(View owner) {
        this(task -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null) minecraft.execute(task);
            else task.run(); // The resolver returns empty without accessing a world.
        }, task -> owner.post(task), id -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null) return "";
            var entity = minecraft.level.getEntity(id);
            if (entity == null || entity.isRemoved()) return "";
            if (entity instanceof ItemEntity dropped) {
                var stack = dropped.getItem();
                return stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            }
            return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        });
    }

    DataLibraryEntityTypes(Consumer<Runnable> gameTasks, Consumer<Runnable> uiTasks,
                           Function<UUID, String> resolve) {
        this.gameTasks = gameTasks;
        this.uiTasks = uiTasks;
        this.resolve = resolve;
    }

    String registryType(DataLibraryUiRepository.Entry entry) {
        return DataLibraryValuePresentation.registryType(entry,
                entry.value() instanceof UUID id ? types.getOrDefault(id, "") : "");
    }

    void refresh(Collection<DataLibraryUiRepository.Entry> entries, Runnable changed) {
        long request = ++generation;
        var ids = new LinkedHashSet<UUID>();
        for (var entry : entries) {
            if (entry.type() == PortType.ENTITY && entry.value() instanceof UUID id) ids.add(id);
        }
        if (ids.isEmpty()) {
            // No asynchronous work or callback is needed: the caller is already rebuilding these rows.
            types = Map.of();
            return;
        }
        gameTasks.accept(() -> {
            if (request != generation) return;
            var resolved = new LinkedHashMap<UUID, String>();
            for (UUID id : ids) {
                if (request != generation) return;
                String type = resolve.apply(id);
                if (type != null && !type.isEmpty()) resolved.put(id, type);
            }
            Map<UUID, String> next = Map.copyOf(resolved);
            uiTasks.accept(() -> {
                if (request != generation || next.equals(types)) return;
                types = next;
                changed.run();
            });
        });
    }

    /** Invalidates pending work before hiding, changing worlds, or disposing the owner. */
    void cancel(boolean clear) {
        generation++;
        if (clear) types = Map.of();
    }
}
