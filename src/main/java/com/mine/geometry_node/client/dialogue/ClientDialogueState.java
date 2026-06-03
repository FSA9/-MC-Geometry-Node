package com.mine.geometry_node.client.dialogue;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketDialogueChoice;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ClientDialogueState {
    @Nullable
    private static PacketOpenDialogue current;

    private ClientDialogueState() {
    }

    public static void handleOpen(PacketOpenDialogue packet) {
        current = packet;
        if (DialogueStyleRenderer.supports(packet.styleId())) {
            DialogueStyleRenderer.open(packet);
        }
    }

    public static void handleClose(PacketCloseDialogue packet) {
        if (current != null && current.sessionId().equals(packet.sessionId())) {
            current = null;
        }
        DialogueStyleRenderer.close(packet.sessionId());
    }

    @Nullable
    public static PacketOpenDialogue current() {
        return current;
    }

    public static boolean choose(String choiceId) {
        if (current == null || choiceId == null || choiceId.isBlank()) {
            return false;
        }
        UUID sessionId = current.sessionId();
        NetworkHandler.sendToServer(new PacketDialogueChoice(sessionId, PacketDialogueChoice.ACTION_CHOOSE, choiceId));
        return true;
    }

    public static boolean close() {
        if (current == null) {
            return false;
        }
        UUID sessionId = current.sessionId();
        NetworkHandler.sendToServer(new PacketDialogueChoice(sessionId, PacketDialogueChoice.ACTION_CLOSE, ""));
        current = null;
        DialogueStyleRenderer.close(sessionId);
        return true;
    }
}
