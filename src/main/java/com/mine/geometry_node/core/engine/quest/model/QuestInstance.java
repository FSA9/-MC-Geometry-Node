package com.mine.geometry_node.core.engine.quest.model;

import net.minecraft.nbt.CompoundTag;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record QuestInstance(UUID instanceId,
                            String taskKey,
                            String statusId,
                            long acceptedAt,
                            long updatedAt,
                            long terminalAt,
                            Map<String, Double> counters) {
    public QuestInstance {
        instanceId = Objects.requireNonNull(instanceId, "instanceId");
        taskKey = Objects.requireNonNullElse(taskKey, "").trim();
        statusId = Objects.requireNonNullElse(statusId, "").trim();
        counters = normalizeCounters(counters);
    }

    public QuestInstance(UUID instanceId,
                         String taskKey,
                         String statusId,
                         long acceptedAt,
                         long updatedAt,
                         long terminalAt) {
        this(instanceId, taskKey, statusId, acceptedAt, updatedAt, terminalAt, Map.of());
    }

    public QuestInstance withStatus(String newStatusId, long changedAt, boolean terminal) {
        return new QuestInstance(
                instanceId,
                taskKey,
                newStatusId,
                acceptedAt,
                changedAt,
                terminal ? changedAt : -1L,
                counters
        );
    }

    public double counter(String key) {
        String resolvedKey = Objects.requireNonNullElse(key, "");
        return resolvedKey.isEmpty() ? 0.0 : counters.getOrDefault(resolvedKey, 0.0);
    }

    public QuestInstance withCounter(String key, double value, long changedAt) {
        String resolvedKey = Objects.requireNonNullElse(key, "");
        if (resolvedKey.isEmpty() || !Double.isFinite(value)) return this;
        double normalizedValue = Math.max(0.0, value);
        Double previousValue = counters.get(resolvedKey);
        if (previousValue != null && Double.compare(previousValue, normalizedValue) == 0) return this;
        Map<String, Double> updatedCounters = new LinkedHashMap<>(counters);
        updatedCounters.put(resolvedKey, normalizedValue);
        return new QuestInstance(
                instanceId, taskKey, statusId, acceptedAt, changedAt, terminalAt, updatedCounters);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("InstanceId", instanceId.toString());
        tag.putString("TaskKey", taskKey);
        tag.putString("StatusId", statusId);
        tag.putLong("AcceptedAt", acceptedAt);
        tag.putLong("UpdatedAt", updatedAt);
        if (terminalAt >= 0L) {
            tag.putLong("TerminalAt", terminalAt);
        }
        if (!counters.isEmpty()) {
            CompoundTag counterTag = new CompoundTag();
            counters.forEach(counterTag::putDouble);
            tag.put("Counters", counterTag);
        }
        return tag;
    }

    public static QuestInstance load(CompoundTag tag) {
        String instanceId = tag.getStringOr("InstanceId", "");
        if (instanceId.isEmpty()) {
            throw new IllegalArgumentException("Quest instance is missing InstanceId");
        }
        CompoundTag counterTag = tag.getCompoundOrEmpty("Counters");
        Map<String, Double> counters = new LinkedHashMap<>();
        for (String key : counterTag.keySet()) {
            double value = counterTag.getDoubleOr(key, 0.0);
            if (Double.isFinite(value)) counters.put(key, value);
        }
        return new QuestInstance(
                UUID.fromString(instanceId),
                tag.getStringOr("TaskKey", ""),
                tag.getStringOr("StatusId", ""),
                tag.getLongOr("AcceptedAt", 0L),
                tag.getLongOr("UpdatedAt", 0L),
                tag.getLongOr("TerminalAt", -1L),
                counters
        );
    }

    private static Map<String, Double> normalizeCounters(Map<String, Double> source) {
        if (source == null || source.isEmpty()) return Map.of();
        Map<String, Double> normalized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String resolvedKey = Objects.requireNonNullElse(key, "");
            if (!resolvedKey.isEmpty() && value != null && Double.isFinite(value)) {
                normalized.put(resolvedKey, Math.max(0.0, value));
            }
        });
        return Map.copyOf(normalized);
    }
}
