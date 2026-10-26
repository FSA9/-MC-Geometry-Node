package com.mine.geometry_node.client.integration.presentation;

import com.mine.geometry_node.client.runtime.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.runtime.dialogue.DialoguePresentation;
import com.mine.geometry_node.client.runtime.quest.ClientQuestScreenState;
import com.mine.geometry_node.client.runtime.quest.QuestScreenPresentation;
import com.mine.geometry_node.client.ui.screen.dialogue.DialogueStyleRenderer;
import com.mine.geometry_node.client.ui.screen.quest.QuestScreenRenderer;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;

import java.util.UUID;

/** Concrete screen wiring. Install before registering any client network receivers. */
public final class RuntimeScreenIntegration {
    private static final DialoguePresentation DIALOGUE = new DialoguePresentation() {
        @Override public boolean supports(String styleId) { return DialogueStyleRenderer.supports(styleId); }
        @Override public void open(PacketOpenDialogue packet) { DialogueStyleRenderer.open(packet); }
        @Override public void close(UUID sessionId) { DialogueStyleRenderer.close(sessionId); }
        @Override public void clear() { DialogueStyleRenderer.clear(); }
    };
    private static final QuestScreenPresentation QUEST = new QuestScreenPresentation() {
        @Override public boolean isActive() { return QuestScreenRenderer.isActive(); }
        @Override public void openOrRefresh(PacketQuestScreenSnapshot snapshot) { QuestScreenRenderer.openOrRefresh(snapshot); }
        @Override public void close() { QuestScreenRenderer.close(); }
        @Override public void clear() { QuestScreenRenderer.clear(); }
    };

    private RuntimeScreenIntegration() {}

    public static void install() {
        ClientDialogueState.installPresentation(DIALOGUE);
        ClientQuestScreenState.installPresentation(QUEST);
    }

    public static void tick() {
        DialogueStyleRenderer.tick();
    }
}
