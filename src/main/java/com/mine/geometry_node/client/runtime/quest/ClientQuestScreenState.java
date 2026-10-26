package com.mine.geometry_node.client.runtime.quest;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketQuestScreenAction;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import org.jetbrains.annotations.Nullable;

public final class ClientQuestScreenState {
    @Nullable
    private static volatile PacketQuestScreenSnapshot current;
    private static volatile boolean previewActive;

    @Nullable
    private static volatile QuestScreenPresentation presentation;

    /** Install once for the client lifetime. Reinstalling the same instance is harmless, including during a session. */
    public static synchronized void installPresentation(QuestScreenPresentation value) {
        java.util.Objects.requireNonNull(value, "presentation");
        if (presentation != null && presentation != value) {
            throw new IllegalStateException("QuestScreenPresentation is already installed; replacing it would detach its screen lifecycle");
        }
        presentation = value;
    }

    private static QuestScreenPresentation presentation() {
        QuestScreenPresentation installed = presentation;
        if (installed == null) {
            throw new IllegalStateException("QuestScreenPresentation must be installed before receiving packets or opening previews");
        }
        return installed;
    }

    private ClientQuestScreenState() {
    }

    public static void handleSnapshot(PacketQuestScreenSnapshot snapshot) {
        QuestScreenPresentation installed = presentation();
        if (!snapshot.openScreen() && !installed.isActive()) {
            return;
        }
        previewActive = false;
        current = snapshot;
        installed.openOrRefresh(snapshot);
    }

    public static boolean openPreview(PacketQuestScreenSnapshot snapshot) {
        if (snapshot == null) return false;
        QuestScreenPresentation installed = presentation();
        previewActive = true;
        current = snapshot;
        installed.openOrRefresh(snapshot);
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
        presentation();
        previewActive = false;
        NetworkHandler.sendToServer(new PacketQuestScreenAction(PacketQuestScreenAction.OPEN, "", ""));
    }

    public static void close() {
        presentation();
        boolean closingPreview = previewActive;
        if (!closingPreview && presentation().isActive()) {
            NetworkHandler.sendToServer(new PacketQuestScreenAction(PacketQuestScreenAction.CLOSE, "", ""));
        }
        previewActive = false;
        if (closingPreview) current = null;
        presentation().close();
    }

    public static void reset() {
        presentation();
        previewActive = false;
        current = null;
        presentation().clear();
    }

    private static boolean send(String action, String taskKey, String instanceId) {
        if (previewActive || !presentation().isActive()) return false;
        NetworkHandler.sendToServer(new PacketQuestScreenAction(action, taskKey, instanceId));
        return true;
    }
}
