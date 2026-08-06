package com.mine.geometry_node.client.quest;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketQuestScreenAction;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import org.jetbrains.annotations.Nullable;

public final class ClientQuestScreenState {
    @Nullable
    private static volatile PacketQuestScreenSnapshot current;
    private static volatile boolean previewActive;

    private ClientQuestScreenState() {
    }

    public static void handleSnapshot(PacketQuestScreenSnapshot snapshot) {
        if (!snapshot.openScreen() && !QuestScreenRenderer.isActive()) {
            return;
        }
        previewActive = false;
        current = snapshot;
        QuestScreenRenderer.openOrRefresh(snapshot);
    }

    public static boolean openPreview(PacketQuestScreenSnapshot snapshot) {
        if (snapshot == null) return false;
        previewActive = true;
        current = snapshot;
        QuestScreenRenderer.openOrRefresh(snapshot);
        return true;
    }

    public static boolean isPreviewActive() {
        return previewActive;
    }

    @Nullable
    public static PacketQuestScreenSnapshot current() {
        return current;
    }

    public static boolean accept(String taskKey) {
        return send(PacketQuestScreenAction.ACCEPT, taskKey, "");
    }

    public static boolean submit(String taskKey, String instanceId) {
        return send(PacketQuestScreenAction.SUBMIT, taskKey, instanceId);
    }

    public static boolean abandon(String taskKey, String instanceId) {
        return send(PacketQuestScreenAction.ABANDON, taskKey, instanceId);
    }

    public static void requestOpen() {
        previewActive = false;
        NetworkHandler.sendToServer(new PacketQuestScreenAction(PacketQuestScreenAction.OPEN, "", ""));
    }

    public static void close() {
        boolean closingPreview = previewActive;
        if (!closingPreview && QuestScreenRenderer.isActive()) {
            NetworkHandler.sendToServer(new PacketQuestScreenAction(PacketQuestScreenAction.CLOSE, "", ""));
        }
        previewActive = false;
        if (closingPreview) current = null;
        QuestScreenRenderer.close();
    }

    public static void reset() {
        previewActive = false;
        current = null;
        QuestScreenRenderer.clear();
    }

    private static boolean send(String action, String taskKey, String instanceId) {
        if (previewActive || !QuestScreenRenderer.isActive()) return false;
        NetworkHandler.sendToServer(new PacketQuestScreenAction(action, taskKey, instanceId));
        return true;
    }
}
