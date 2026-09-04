package com.mine.geometry_node.core.engine.system.quest;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.api.EventPayload;
import com.mine.geometry_node.api.GeometryNodeEvents;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventFields;
import com.mine.geometry_node.core.engine.blueprint.plan.BlueprintPlan;
import com.mine.geometry_node.core.engine.graph.GraphTypeRegistry;
import com.mine.geometry_node.core.engine.graph.runtime.GraphCloseMode;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.system.quest.model.QuestConditionResult;
import com.mine.geometry_node.core.engine.system.quest.model.QuestInstance;
import com.mine.geometry_node.core.engine.system.quest.model.QuestListEntry;
import com.mine.geometry_node.core.engine.system.quest.model.QuestOperationResult;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatus;
import com.mine.geometry_node.core.engine.system.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.engine.system.quest.storage.EntityQuestAttachment;
import net.minecraft.world.entity.Entity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class QuestService {
    public static final QuestService INSTANCE = new QuestService();
    public static final String REQUEST_SOURCE_BLUEPRINT = "blueprint";

    private boolean initialized;

    private QuestService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;

        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent event) -> restoreBindings(event.getEntity()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent event) -> restoreBindings(event.getEntity()));
    }

    public QuestOperationResult addToList(Entity owner, String taskKey) {
        return addToList(owner, taskKey, true, true, "");
    }

    public QuestOperationResult addToList(Entity owner,
                                          String taskKey,
                                          boolean visible,
                                          boolean acceptEnabled,
                                          @Nullable String sourceId) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation != null) return validation;

        String normalizedKey = normalizeTaskKey(taskKey);
        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance existingInstance = attachment.getCurrentInstance(normalizedKey);
        if (blocksNewInstance(existingInstance)) {
            return QuestOperationResult.of(QuestOperationResult.Code.ALREADY_EXISTS, existingInstance);
        }

        QuestListEntry existingEntry = attachment.getEntry(normalizedKey);
        QuestListEntry entry = new QuestListEntry(
                normalizedKey,
                visible,
                acceptEnabled,
                sourceId,
                existingEntry != null ? existingEntry.publishedAt() : currentTime(owner)
        );
        attachment.putEntry(entry);
        QuestOperationResult result = QuestOperationResult.of(
                existingEntry != null && existingEntry.equals(entry)
                        ? QuestOperationResult.Code.NO_CHANGE
                        : QuestOperationResult.Code.SUCCESS
        );
        return result;
    }

    public QuestOperationResult accept(Entity owner, String taskKey) {
        return accept(owner, taskKey, REQUEST_SOURCE_BLUEPRINT);
    }

    public QuestOperationResult accept(Entity owner, String taskKey, @Nullable String requestSource) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation != null) return validation;

        String normalizedKey = normalizeTaskKey(taskKey);
        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance existing = attachment.getCurrentInstance(normalizedKey);
        if (blocksNewInstance(existing)) {
            return QuestOperationResult.of(QuestOperationResult.Code.ALREADY_EXISTS, existing);
        }

        QuestListEntry entry = attachment.getEntry(normalizedKey);
        if (entry != null && !entry.acceptEnabled()) {
            return QuestOperationResult.of(QuestOperationResult.Code.NOT_ACCEPTABLE);
        }

        QuestConditionResult eligibility = QuestConditionService.INSTANCE.evaluate(
                owner, normalizedKey, QuestConditionKind.ACCEPTANCE);
        if (!eligibility.evaluated()) {
            return QuestOperationResult.of(QuestOperationResult.Code.CONDITION_EVALUATION_FAILED);
        }
        if (!eligibility.allowed()) {
            return QuestOperationResult.failure(
                    QuestOperationResult.Code.CONDITION_NOT_MET,
                    eligibility.firstFailureReason());
        }

        long now = currentTime(owner);
        QuestInstance instance = new QuestInstance(
                UUID.randomUUID(),
                normalizedKey,
                QuestStatusRegistry.IN_PROGRESS.id(),
                now,
                now,
                -1L
        );
        attachment.putNewInstance(instance);
        attachment.removeEntry(normalizedKey);
        BlueprintRuntime.INSTANCE.bindGraph(owner, normalizedKey);
        dispatchLifecycle(owner, normalizedKey, QuestEventTypes.STATUS_CHANGED, instance,
                QuestStatusRegistry.UNACCEPTED.id(), instance.statusId(), "", requestSource);
        syncBindingToCurrentStatus(owner, normalizedKey);
        return QuestOperationResult.of(QuestOperationResult.Code.SUCCESS,
                attachment.getCurrentInstance(normalizedKey));
    }

    public QuestOperationResult setStatus(Entity owner, String taskKey, String statusId) {
        return setStatus(owner, taskKey, statusId, "", REQUEST_SOURCE_BLUEPRINT);
    }

    public QuestOperationResult setStatus(Entity owner,
                                          String taskKey,
                                          String statusId,
                                          @Nullable String reason,
                                          @Nullable String requestSource) {
        return setStatusInternal(owner, taskKey, statusId, reason, requestSource, true);
    }

    public QuestOperationResult forceSetStatus(Entity owner,
                                               String taskKey,
                                               String statusId,
                                               @Nullable String reason,
                                               @Nullable String requestSource) {
        return setStatusInternal(owner, taskKey, statusId, reason, requestSource, false);
    }

    private QuestOperationResult setStatusInternal(Entity owner,
                                                   String taskKey,
                                                   String statusId,
                                                   @Nullable String reason,
                                                   @Nullable String requestSource,
                                                   boolean checkCompletionConditions) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation != null) return validation;

        QuestStatus status = QuestStatusRegistry.INSTANCE.get(statusId);
        if (status == null || !status.assignable()) {
            return QuestOperationResult.of(QuestOperationResult.Code.INVALID_STATUS);
        }

        String normalizedKey = normalizeTaskKey(taskKey);
        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance existing = attachment.getCurrentInstance(normalizedKey);
        if (existing == null) {
            return QuestOperationResult.of(QuestOperationResult.Code.INSTANCE_NOT_FOUND);
        }
        if (existing.statusId().equals(status.id())) {
            return QuestOperationResult.of(QuestOperationResult.Code.NO_CHANGE, existing);
        }

        if (checkCompletionConditions && QuestStatusRegistry.COMPLETED.id().equals(status.id())) {
            QuestConditionResult completion = QuestConditionService.INSTANCE.evaluate(
                    owner, normalizedKey, QuestConditionKind.COMPLETION);
            if (!completion.evaluated()) {
                return QuestOperationResult.of(
                        QuestOperationResult.Code.COMPLETION_CONDITION_EVALUATION_FAILED);
            }
            if (!completion.allowed()) {
                return QuestOperationResult.failure(
                        QuestOperationResult.Code.COMPLETION_CONDITION_NOT_MET,
                        completion.firstFailureReason());
            }
        }

        QuestInstance updated = existing.withStatus(status.id(), currentTime(owner), status.terminal());
        attachment.updateCurrentInstance(updated);
        // A lifecycle branch must be reachable even for inactive-to-inactive
        // transitions; the final status below decides whether this binding stays.
        bindIfValidQuest(owner, normalizedKey);
        dispatchLifecycle(owner, normalizedKey, QuestEventTypes.STATUS_CHANGED, updated,
                existing.statusId(), updated.statusId(), reason, requestSource);
        syncBindingToCurrentStatus(owner, normalizedKey);
        return QuestOperationResult.of(QuestOperationResult.Code.SUCCESS,
                attachment.getCurrentInstance(normalizedKey));
    }

    public QuestOperationResult submit(Entity owner, String taskKey) {
        return submit(owner, taskKey, "", REQUEST_SOURCE_BLUEPRINT);
    }

    public QuestOperationResult submit(Entity owner,
                                       String taskKey,
                                       @Nullable String reason,
                                       @Nullable String requestSource) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation != null) return validation;

        String normalizedKey = normalizeTaskKey(taskKey);
        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance instance = attachment.getCurrentInstance(normalizedKey);
        if (instance == null) {
            return QuestOperationResult.of(QuestOperationResult.Code.INSTANCE_NOT_FOUND);
        }

        QuestStatus status = QuestStatusRegistry.INSTANCE.get(instance.statusId());
        if (status == null || !status.graphActive()) {
            return QuestOperationResult.of(QuestOperationResult.Code.NOT_SUBMITTABLE, instance);
        }

        return setStatus(
                owner,
                normalizedKey,
                QuestStatusRegistry.COMPLETED.id(),
                reason,
                requestSource);
    }

    public QuestOperationResult abandon(Entity owner, String taskKey) {
        return abandon(owner, taskKey, "", REQUEST_SOURCE_BLUEPRINT);
    }

    public QuestOperationResult abandon(Entity owner,
                                        String taskKey,
                                        @Nullable String reason,
                                        @Nullable String requestSource) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation != null) return validation;

        String normalizedKey = normalizeTaskKey(taskKey);
        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance existing = attachment.getCurrentInstance(normalizedKey);
        if (existing == null) {
            return QuestOperationResult.of(QuestOperationResult.Code.INSTANCE_NOT_FOUND);
        }

        QuestStatus existingStatus = QuestStatusRegistry.INSTANCE.get(existing.statusId());
        if (existingStatus == null || !existingStatus.graphActive()) {
            return QuestOperationResult.of(QuestOperationResult.Code.NOT_ABANDONABLE, existing);
        }

        QuestStatus abandonedStatus = QuestStatusRegistry.ABANDONED;
        QuestInstance abandoned = existing.withStatus(
                abandonedStatus.id(), currentTime(owner), abandonedStatus.terminal());
        attachment.updateCurrentInstance(abandoned);

        bindIfValidQuest(owner, normalizedKey);
        dispatchLifecycle(owner, normalizedKey, QuestEventTypes.STATUS_CHANGED, abandoned,
                existing.statusId(), abandoned.statusId(), reason, requestSource);
        syncBindingToCurrentStatus(owner, normalizedKey);
        return QuestOperationResult.of(QuestOperationResult.Code.SUCCESS,
                attachment.getCurrentInstance(normalizedKey));
    }

    @Nullable
    public QuestInstance findCurrent(Entity owner, String taskKey) {
        if (owner == null) return null;
        String normalizedKey = normalizeTaskKey(taskKey);
        return normalizedKey.isEmpty() ? null : EntityQuestAttachment.get(owner).getCurrentInstance(normalizedKey);
    }

    @Nullable
    public QuestInstance findByInstanceId(Entity owner, UUID instanceId) {
        if (owner == null || instanceId == null) return null;
        return EntityQuestAttachment.get(owner).getInstance(instanceId);
    }

    @Nullable
    public QuestListEntry findListEntry(Entity owner, String taskKey) {
        if (owner == null) return null;
        String normalizedKey = normalizeTaskKey(taskKey);
        return normalizedKey.isEmpty() ? null : EntityQuestAttachment.get(owner).getEntry(normalizedKey);
    }

    public Collection<QuestListEntry> getEntries(Entity owner) {
        return owner != null ? EntityQuestAttachment.get(owner).getEntries() : List.of();
    }

    public Collection<QuestInstance> getCurrentInstances(Entity owner) {
        return owner != null ? EntityQuestAttachment.get(owner).getCurrentInstances() : List.of();
    }

    public Collection<QuestInstance> getAllInstances(Entity owner) {
        return owner != null ? EntityQuestAttachment.get(owner).getAllInstances() : List.of();
    }

    public List<QuestInstance> getHistory(Entity owner, String taskKey) {
        if (owner == null) return List.of();
        String normalizedKey = normalizeTaskKey(taskKey);
        return normalizedKey.isEmpty()
                ? List.of()
                : EntityQuestAttachment.get(owner).getHistory(normalizedKey);
    }

    public QuestOperationResult setCounter(Entity owner, String taskKey, String counterKey, double value) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation != null) return validation;
        if (!Double.isFinite(value)) {
            return QuestOperationResult.of(QuestOperationResult.Code.INVALID_COUNTER_VALUE);
        }

        String normalizedKey = normalizeTaskKey(taskKey);
        String resolvedCounterKey = counterKey != null ? counterKey : "";
        if (resolvedCounterKey.isEmpty()) {
            return QuestOperationResult.of(QuestOperationResult.Code.INVALID_COUNTER_KEY);
        }

        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance existing = attachment.getCurrentInstance(normalizedKey);
        if (existing == null) {
            return QuestOperationResult.of(QuestOperationResult.Code.INSTANCE_NOT_FOUND);
        }
        QuestInstance updated = existing.withCounter(resolvedCounterKey, value, currentTime(owner));
        if (updated == existing) {
            return QuestOperationResult.of(QuestOperationResult.Code.NO_CHANGE, existing);
        }
        attachment.updateCurrentInstance(updated);
        return QuestOperationResult.of(QuestOperationResult.Code.SUCCESS, updated);
    }

    /**
     * Returns the current value and persists a zero-valued counter when the input
     * does not exist yet. This is intentionally not a pure read operation.
     */
    public double getOrCreateCounter(Entity owner, String taskKey, String counterKey) {
        if (!isServerOwner(owner)) return 0.0;

        String normalizedTaskKey = normalizeTaskKey(taskKey);
        String resolvedCounterKey = counterKey != null ? counterKey : "";
        if (normalizedTaskKey.isEmpty() || resolvedCounterKey.isEmpty()) return 0.0;

        EntityQuestAttachment attachment = EntityQuestAttachment.get(owner);
        QuestInstance instance = attachment.getCurrentInstance(normalizedTaskKey);
        if (instance == null) return 0.0;
        if (instance.counters().containsKey(resolvedCounterKey)) {
            return instance.counter(resolvedCounterKey);
        }

        QuestOperationResult initialization = setCounter(
                owner, normalizedTaskKey, resolvedCounterKey, 0.0);
        QuestInstance initialized = initialization.instance();
        return initialization.successful() && initialized != null
                ? initialized.counter(resolvedCounterKey)
                : 0.0;
    }

    public void restoreBindings(Entity owner) {
        if (!isServerOwner(owner)) return;
        for (QuestInstance instance : EntityQuestAttachment.get(owner).getCurrentInstances()) {
            QuestStatus status = QuestStatusRegistry.INSTANCE.get(instance.statusId());
            if (status != null && status.graphActive()) {
                bindIfValidQuest(owner, instance.taskKey());
            }
        }
    }

    @Nullable
    private QuestOperationResult validateQuest(Entity owner, String taskKey) {
        if (!isServerOwner(owner)) {
            return QuestOperationResult.of(QuestOperationResult.Code.INVALID_OWNER);
        }
        String normalizedKey = normalizeTaskKey(taskKey);
        if (normalizedKey.isEmpty()) {
            return QuestOperationResult.of(QuestOperationResult.Code.INVALID_TASK_KEY);
        }
        BlueprintPlan index = BlueprintRuntime.INSTANCE.getGraphIndex(normalizedKey);
        if (index == null) {
            return QuestOperationResult.of(QuestOperationResult.Code.TASK_NOT_FOUND);
        }
        if (!GraphTypeRegistry.QUEST.id().equals(index.getGraphTypeId())) {
            GeometryNode.LOGGER.warn(
                    "Rejected non-quest graph for quest operation: path={}, graphType={}",
                    normalizedKey,
                    index.getGraphTypeId()
            );
            return QuestOperationResult.of(QuestOperationResult.Code.NOT_A_QUEST_GRAPH);
        }
        return null;
    }

    private void bindIfValidQuest(Entity owner, String taskKey) {
        QuestOperationResult validation = validateQuest(owner, taskKey);
        if (validation == null) {
            BlueprintRuntime.INSTANCE.bindGraph(owner, taskKey);
        } else {
            GeometryNode.LOGGER.warn("Cannot restore quest graph binding: taskKey={}, reason={}", taskKey, validation.code());
        }
    }

    private void dispatchLifecycle(Entity owner,
                                   String taskKey,
                                   String eventType,
                                   QuestInstance instance,
                                   @Nullable String oldStatus,
                                   @Nullable String newStatus,
                                   @Nullable String reason,
                                   @Nullable String requestSource) {
        if (!(owner.level() instanceof ServerLevel level)) return;
        // Lifecycle changes are entity-level events. Each subscribed node decides
        // whether it listens to every task or only to its owning quest graph.
        GeometryNodeEvents.dispatch(level, owner, eventType,
                EventPayload.of(
                        GraphEventFields.TASK_KEY, taskKey,
                        GraphEventFields.INSTANCE_ID, instance.instanceId().toString(),
                        GraphEventFields.OLD_STATUS, normalizeText(oldStatus),
                        GraphEventFields.NEW_STATUS, normalizeText(newStatus),
                        GraphEventFields.REASON, normalizeText(reason),
                        GraphEventFields.REQUEST_SOURCE, normalizeText(requestSource)
                ));
    }

    private void syncBindingToCurrentStatus(Entity owner, String taskKey) {
        QuestInstance current = EntityQuestAttachment.get(owner).getCurrentInstance(taskKey);
        QuestStatus currentStatus = current != null
                ? QuestStatusRegistry.INSTANCE.get(current.statusId())
                : null;
        if (currentStatus != null && currentStatus.graphActive()) {
            bindIfValidQuest(owner, taskKey);
        } else {
            BlueprintRuntime.INSTANCE.unbindGraph(owner, taskKey, GraphCloseMode.DRAIN);
        }
    }

    private static boolean isServerOwner(@Nullable Entity owner) {
        return owner != null && !owner.level().isClientSide();
    }

    private static boolean blocksNewInstance(@Nullable QuestInstance instance) {
        if (instance == null) return false;
        QuestStatus status = QuestStatusRegistry.INSTANCE.get(instance.statusId());
        // An unknown status is preserved and blocks replacement. This avoids losing
        // a live custom-status instance while its registration is temporarily absent.
        return status == null || !status.terminal();
    }

    private static String normalizeTaskKey(@Nullable String taskKey) {
        if (taskKey == null || taskKey.isBlank()) return "";

        String graphPath = taskKey.trim().replace('\\', '/');
        String lowerPath = graphPath.toLowerCase(Locale.ROOT);
        boolean remotePath = false;
        if (lowerPath.startsWith("remote://")) {
            graphPath = graphPath.substring("remote://".length());
            remotePath = true;
        } else if (lowerPath.startsWith("remote:/")) {
            graphPath = graphPath.substring("remote:/".length());
            remotePath = true;
        } else if (lowerPath.startsWith("remote:")) {
            graphPath = graphPath.substring("remote:".length());
            remotePath = true;
        }
        while (remotePath && graphPath.startsWith("/")) {
            graphPath = graphPath.substring(1);
        }

        try {
            return BlueprintRuntime.INSTANCE.resolveGraphId(graphPath);
        } catch (IllegalArgumentException exception) {
            GeometryNode.LOGGER.warn(
                    "Rejected invalid quest graph path: path={}, reason={}",
                    taskKey,
                    exception.getMessage()
            );
            return "";
        }
    }

    private static long currentTime(Entity owner) {
        return owner.level().getGameTime();
    }

    private static String normalizeText(@Nullable String value) {
        return value != null ? value.trim() : "";
    }
}
