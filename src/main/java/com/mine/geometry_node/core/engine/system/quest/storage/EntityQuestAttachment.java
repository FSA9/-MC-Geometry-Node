package com.mine.geometry_node.core.engine.system.quest.storage;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestInstance;
import com.mine.geometry_node.core.engine.system.quest.model.QuestListEntry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class EntityQuestAttachment {
    private static final int DATA_VERSION = 4;

    // Independent of publication and instance lifetime, including tasks not yet accepted.
    private final Map<String, EnumMap<QuestConditionKind, Boolean>> conditionStates = new LinkedHashMap<>();
    private final Map<String, QuestListEntry> entries = new LinkedHashMap<>();
    private final Map<UUID, QuestInstance> instancesById = new LinkedHashMap<>();
    private final Map<String, UUID> currentInstanceIds = new LinkedHashMap<>();

    public static EntityQuestAttachment get(Entity entity) {
        return entity.getData(GeometryNode.QUEST_DATA_ATTACHMENT);
    }

    public boolean isConditionEnabled(String taskKey, QuestConditionKind kind) {
        Objects.requireNonNull(kind, "kind");
        Map<QuestConditionKind, Boolean> states = conditionStates.get(taskKey);
        return states == null || states.getOrDefault(kind, true);
    }

    /** Returns whether the stored setting changed; explicit true also survives save/load. */
    public boolean setCondition(String taskKey, QuestConditionKind kind, boolean enabled) {
        if (taskKey == null || taskKey.isBlank()) {
            throw new IllegalArgumentException("Quest condition requires a task key");
        }
        Objects.requireNonNull(kind, "kind");
        Boolean previous = conditionStates.computeIfAbsent(taskKey, ignored -> new EnumMap<>(QuestConditionKind.class))
                .put(kind, enabled);
        return previous == null || previous != enabled;
    }

    public Collection<QuestListEntry> getEntries() {
        return Collections.unmodifiableCollection(entries.values());
    }

    public Collection<QuestInstance> getCurrentInstances() {
        List<QuestInstance> result = new ArrayList<>(currentInstanceIds.size());
        for (UUID instanceId : currentInstanceIds.values()) {
            QuestInstance instance = instancesById.get(instanceId);
            if (instance != null) result.add(instance);
        }
        return List.copyOf(result);
    }

    public Collection<QuestInstance> getAllInstances() {
        return Collections.unmodifiableCollection(instancesById.values());
    }

    public List<QuestInstance> getHistory(String taskKey) {
        if (taskKey == null || taskKey.isEmpty()) return List.of();
        return instancesById.values().stream()
                .filter(instance -> taskKey.equals(instance.taskKey()))
                .toList();
    }

    @Nullable
    public QuestListEntry getEntry(String taskKey) {
        return entries.get(taskKey);
    }

    @Nullable
    public QuestInstance getCurrentInstance(String taskKey) {
        UUID instanceId = currentInstanceIds.get(taskKey);
        return instanceId != null ? instancesById.get(instanceId) : null;
    }

    @Nullable
    public QuestInstance getInstance(UUID instanceId) {
        return instanceId != null ? instancesById.get(instanceId) : null;
    }

    public void putEntry(QuestListEntry entry) {
        if (entry != null && !entry.taskKey().isEmpty()) {
            entries.put(entry.taskKey(), entry);
        }
    }

    public void removeEntry(String taskKey) {
        entries.remove(taskKey);
    }

    public void putNewInstance(QuestInstance instance) {
        if (instance != null && !instance.taskKey().isEmpty()) {
            QuestInstance existing = instancesById.putIfAbsent(instance.instanceId(), instance);
            if (existing != null && !existing.equals(instance)) {
                throw new IllegalStateException("Duplicate quest instance ID: " + instance.instanceId());
            }
            currentInstanceIds.put(instance.taskKey(), instance.instanceId());
        }
    }

    public void updateCurrentInstance(QuestInstance instance) {
        if (instance == null || instance.taskKey().isEmpty()) return;
        UUID currentId = currentInstanceIds.get(instance.taskKey());
        if (!instance.instanceId().equals(currentId)) {
            throw new IllegalStateException(
                    "Cannot update non-current quest instance: " + instance.instanceId());
        }
        instancesById.put(instance.instanceId(), instance);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("DataVersion", DATA_VERSION);

        ListTag conditionList = new ListTag();
        for (Map.Entry<String, EnumMap<QuestConditionKind, Boolean>> condition : conditionStates.entrySet()) {
            CompoundTag conditionTag = new CompoundTag();
            conditionTag.putString("TaskKey", condition.getKey());
            condition.getValue().forEach((kind, enabled) -> conditionTag.putBoolean(kind.name(), enabled));
            conditionList.add(conditionTag);
        }
        if (!conditionList.isEmpty()) {
            tag.put("ConditionStates", conditionList);
        }

        ListTag entryList = new ListTag();
        for (QuestListEntry entry : entries.values()) {
            entryList.add(entry.save());
        }
        if (!entryList.isEmpty()) {
            tag.put("Entries", entryList);
        }

        ListTag instanceList = new ListTag();
        for (QuestInstance instance : instancesById.values()) {
            instanceList.add(instance.save());
        }
        if (!instanceList.isEmpty()) {
            tag.put("Instances", instanceList);
        }

        ListTag currentList = new ListTag();
        for (Map.Entry<String, UUID> current : currentInstanceIds.entrySet()) {
            CompoundTag currentTag = new CompoundTag();
            currentTag.putString("TaskKey", current.getKey());
            currentTag.putString("InstanceId", current.getValue().toString());
            currentList.add(currentTag);
        }
        if (!currentList.isEmpty()) {
            tag.put("CurrentInstances", currentList);
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        conditionStates.clear();
        entries.clear();
        instancesById.clear();
        currentInstanceIds.clear();

        ListTag entryList = tag.getListOrEmpty("Entries");
        for (int i = 0; i < entryList.size(); i++) {
            QuestListEntry entry = QuestListEntry.load(entryList.getCompoundOrEmpty(i));
            if (!entry.taskKey().isEmpty()) {
                entries.put(entry.taskKey(), entry);
                // Legacy entry flags become task-level gates, so acceptance cannot erase them.
                if (tag.getIntOr("DataVersion", 0) < 4) {
                    setCondition(entry.taskKey(), QuestConditionKind.VISIBILITY, entry.visible());
                    setCondition(entry.taskKey(), QuestConditionKind.ACCEPTANCE, entry.acceptEnabled());
                }
            }
        }

        ListTag conditionList = tag.getListOrEmpty("ConditionStates");
        for (int i = 0; i < conditionList.size(); i++) {
            CompoundTag conditionTag = conditionList.getCompoundOrEmpty(i);
            String taskKey = conditionTag.getStringOr("TaskKey", "");
            if (taskKey.isBlank()) continue;
            for (QuestConditionKind kind : QuestConditionKind.values()) {
                if (conditionTag.contains(kind.name())) {
                    setCondition(taskKey, kind, conditionTag.getBooleanOr(kind.name(), true));
                }
            }
        }

        ListTag instanceList = tag.getListOrEmpty("Instances");
        for (int i = 0; i < instanceList.size(); i++) {
            try {
                QuestInstance instance = QuestInstance.load(instanceList.getCompoundOrEmpty(i));
                if (!instance.taskKey().isEmpty()) {
                    instancesById.put(instance.instanceId(), instance);
                    // Version 2 had one stored instance per task. Iteration order also
                    // provides a safe fallback if a version 3 current index is damaged.
                    currentInstanceIds.put(instance.taskKey(), instance.instanceId());
                }
            } catch (IllegalArgumentException exception) {
                GeometryNode.LOGGER.warn("Skipping invalid quest instance in entity attachment", exception);
            }
        }

        ListTag currentList = tag.getListOrEmpty("CurrentInstances");
        for (int i = 0; i < currentList.size(); i++) {
            CompoundTag currentTag = currentList.getCompoundOrEmpty(i);
            String taskKey = currentTag.getStringOr("TaskKey", "");
            String rawInstanceId = currentTag.getStringOr("InstanceId", "");
            try {
                UUID instanceId = UUID.fromString(rawInstanceId);
                QuestInstance instance = instancesById.get(instanceId);
                if (instance != null && taskKey.equals(instance.taskKey())) {
                    currentInstanceIds.put(taskKey, instanceId);
                }
            } catch (IllegalArgumentException exception) {
                GeometryNode.LOGGER.warn(
                        "Skipping invalid current quest instance reference: taskKey={}, instanceId={}",
                        taskKey, rawInstanceId);
            }
        }
    }
}
