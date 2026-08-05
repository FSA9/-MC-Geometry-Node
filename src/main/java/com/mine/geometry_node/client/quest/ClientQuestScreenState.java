package com.mine.geometry_node.client.quest;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketQuestScreenAction;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import org.jetbrains.annotations.Nullable;

public final class ClientQuestScreenState {
    @Nullable
    private static volatile PacketQuestScreenSnapshot current;

    private ClientQuestScreenState() {
    }

    public static void handleSnapshot(PacketQuestScreenSnapshot snapshot) {
        if (!snapshot.openScreen() && !QuestScreenRenderer.isActive()) {
            return;
        }
        current = snapshot;
        QuestScreenRenderer.openOrRefresh(snapshot);
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
        NetworkHandler.sendToServer(new PacketQuestScreenAction(PacketQuestScreenAction.OPEN, "", ""));
    }

    public static void close() {
        if (QuestScreenRenderer.isActive()) {
            NetworkHandler.sendToServer(new PacketQuestScreenAction(PacketQuestScreenAction.CLOSE, "", ""));
        }
        QuestScreenRenderer.close();
    }

    public static void reset() {
        current = null;
        QuestScreenRenderer.clear();
    }

    private static boolean send(String action, String taskKey, String instanceId) {
        if (!QuestScreenRenderer.isActive()) return false;
        NetworkHandler.sendToServer(new PacketQuestScreenAction(action, taskKey, instanceId));
        return true;
    }
}
