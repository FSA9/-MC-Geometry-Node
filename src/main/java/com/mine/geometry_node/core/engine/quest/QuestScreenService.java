package com.mine.geometry_node.core.engine.quest;

import com.mine.geometry_node.core.engine.blueprint.runtime.GraphEngine;
import com.mine.geometry_node.core.engine.blueprint.runtime.RuntimeGraphIndex;
import com.mine.geometry_node.core.engine.quest.model.QuestDefinition;
import com.mine.geometry_node.core.engine.quest.model.QuestConditionKind;
import com.mine.geometry_node.core.engine.quest.model.QuestConditionResult;
import com.mine.geometry_node.core.engine.quest.model.QuestInstance;
import com.mine.geometry_node.core.engine.quest.model.QuestListEntry;
import com.mine.geometry_node.core.engine.quest.model.QuestOperationResult;
import com.mine.geometry_node.core.engine.quest.status.QuestStatusRegistry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketQuestScreenAction;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Single server-side entrypoint for immutable quest-screen sessions. */
public final class QuestScreenService {
    public static final QuestScreenService INSTANCE = new QuestScreenService();
    public static final String REQUEST_SOURCE_UI = "quest_ui";

    private final Map<UUID, OpenSnapshot> openSnapshots = new LinkedHashMap<>();
    private boolean initialized;

    private QuestScreenService() {
    }

    public synchronized void init() {
        if (initialized) return;
        initialized = true;
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) ->
                openSnapshots.remove(event.getEntity().getUUID()));
    }

    public void open(ServerPlayer player) {
        if (player != null) {
            OpenSnapshot snapshot = createSnapshot(player);
            openSnapshots.put(player.getUUID(), snapshot);
            sendSnapshot(player, snapshot, null, true);
        }
    }

    public void handleAction(ServerPlayer player, PacketQuestScreenAction request) {
        if (player == null || request == null) return;

        if (PacketQuestScreenAction.OPEN.equals(request.action())) {
            open(player);
            return;
        }
        if (PacketQuestScreenAction.CLOSE.equals(request.action())) {
            openSnapshots.remove(player.getUUID());
            return;
        }
        OpenSnapshot snapshot = openSnapshots.get(player.getUUID());
        if (snapshot == null) return;

        QuestOperationResult result = null;
        switch (request.action()) {
            case PacketQuestScreenAction.ACCEPT -> {
                QuestListEntry entry = QuestService.INSTANCE.findListEntry(player, request.taskKey());
                if (entry == null || !entry.visible() || !entry.acceptEnabled()) {
                    result = QuestOperationResult.of(QuestOperationResult.Code.NOT_ACCEPTABLE);
                } else {
                    result = QuestService.INSTANCE.accept(player, request.taskKey(), REQUEST_SOURCE_UI);
                }
            }
            case PacketQuestScreenAction.SUBMIT -> {
                QuestInstance current = QuestService.INSTANCE.findCurrent(player, request.taskKey());
                if (current == null || !current.instanceId().toString().equals(request.instanceId())) {
                    result = QuestOperationResult.of(QuestOperationResult.Code.INSTANCE_NOT_FOUND);
                } else {
                    result = QuestService.INSTANCE.submit(player, request.taskKey(), "", REQUEST_SOURCE_UI);
                }
            }
            case PacketQuestScreenAction.ABANDON -> {
                QuestInstance current = QuestService.INSTANCE.findCurrent(player, request.taskKey());
                if (current == null || !current.instanceId().toString().equals(request.instanceId())) {
                    result = QuestOperationResult.of(QuestOperationResult.Code.INSTANCE_NOT_FOUND);
                } else {
                    result = QuestService.INSTANCE.abandon(player, request.taskKey(), "", REQUEST_SOURCE_UI);
                }
            }
            default -> result = QuestOperationResult.of(QuestOperationResult.Code.INVALID_TASK_KEY);
        }
        // Keep the view stable for the lifetime of this open session. Actions return
        // their result, but changed quest data is read only when the screen is reopened.
        sendSnapshot(player, snapshot, result, false);
    }

    private void sendSnapshot(ServerPlayer player,
                              OpenSnapshot snapshot,
                              @Nullable QuestOperationResult result,
                              boolean openScreen) {
        NetworkHandler.sendToPlayer(player, new PacketQuestScreenSnapshot(
                snapshot.statuses(),
                snapshot.quests(),
                openScreen,
                result == null || result.successful(),
                result == null ? "" : result.code().name().toLowerCase(Locale.ROOT),
                result == null ? "" : result.message()
        ));
    }

    private static OpenSnapshot createSnapshot(ServerPlayer player) {
        List<PacketQuestScreenSnapshot.StatusView> statuses = createStatuses();
        return new OpenSnapshot(statuses, createQuests(player, statuses));
    }

    private record OpenSnapshot(List<PacketQuestScreenSnapshot.StatusView> statuses,
                                List<PacketQuestScreenSnapshot.QuestView> quests) {
        private OpenSnapshot {
            statuses = List.copyOf(statuses);
            quests = List.copyOf(quests);
        }
    }

    private static List<PacketQuestScreenSnapshot.StatusView> createStatuses() {
        return QuestScreenViewFactory.statuses();
    }

    private static List<PacketQuestScreenSnapshot.QuestView> createQuests(
            ServerPlayer player,
            List<PacketQuestScreenSnapshot.StatusView> statuses) {
        List<PacketQuestScreenSnapshot.QuestView> result = new ArrayList<>();
        Set<String> knownStatuses = new HashSet<>();
        Set<String> presentedEntryKeys = new HashSet<>();
        for (PacketQuestScreenSnapshot.StatusView status : statuses) {
            knownStatuses.add(status.id());
        }

        for (QuestListEntry entry : QuestService.INSTANCE.getEntries(player)) {
            if (!entry.visible()) continue;
            QuestConditionResult visibility = QuestConditionService.INSTANCE.evaluate(
                    player, entry.taskKey(), QuestConditionKind.VISIBILITY);
            if (!visibility.evaluated() || !visibility.allowed()) continue;
            PacketQuestScreenSnapshot.QuestView view = createView(
                    player, entry.taskKey(), "", QuestStatusRegistry.UNACCEPTED.id(),
                    entry.acceptEnabled(), entry.publishedAt(), null);
            if (view != null) {
                result.add(view);
                presentedEntryKeys.add(entry.taskKey());
            }
        }

        for (QuestInstance instance : QuestService.INSTANCE.getCurrentInstances(player)) {
            // A republished task represents the next run in the normal player UI.
            // Its previous terminal run remains available through quest history APIs.
            if (presentedEntryKeys.contains(instance.taskKey())) continue;
            if (!knownStatuses.contains(instance.statusId())) continue;
            PacketQuestScreenSnapshot.QuestView view = createView(
                    player, instance.taskKey(), instance.instanceId().toString(), instance.statusId(),
                    false, instance.updatedAt(), instance);
            if (view != null) result.add(view);
        }

        result.sort(Comparator.comparingLong(PacketQuestScreenSnapshot.QuestView::updatedAt).reversed());
        return List.copyOf(result);
    }

    @Nullable
    private static PacketQuestScreenSnapshot.QuestView createView(
            ServerPlayer player,
            String taskKey,
            String instanceId,
            String statusId,
            boolean acceptEnabled,
            long updatedAt,
            @Nullable QuestInstance instance) {
        RuntimeGraphIndex index = GraphEngine.getGraphIndex(taskKey);
        if (index == null) return null;
        QuestDefinition definition = index.getQuestDefinition();
        return QuestScreenViewFactory.quest(
                taskKey,
                instanceId,
                statusId,
                acceptEnabled,
                updatedAt,
                definition,
                index.getQuestConditionOverview(),
                kind -> QuestConditionService.INSTANCE.evaluateChecks(player, taskKey, kind),
                instance == null ? null : instance::counter
        );
    }
}
