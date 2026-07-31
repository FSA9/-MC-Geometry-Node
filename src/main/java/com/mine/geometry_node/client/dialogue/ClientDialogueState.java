package com.mine.geometry_node.client.dialogue;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketDialogueChoice;
import com.mine.geometry_node.core.network.packet.c2s.PacketShopTradeRequest;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class ClientDialogueState {
    @Nullable
    private static volatile PacketOpenDialogue current;

    private ClientDialogueState() {
    }

    public static void handleOpen(PacketOpenDialogue packet) {
        if (!DialogueStyleRenderer.supports(packet.styleId())) {
            GeometryNode.LOGGER.warn(
                    "[ClientDialogueState] No renderer is registered for dialogue style '{}'; closing session {}.",
                    packet.styleId(),
                    packet.sessionId()
            );
            clearSession(packet.sessionId());
            NetworkHandler.sendToServer(new PacketDialogueChoice(
                    packet.sessionId(),
                    PacketDialogueChoice.ACTION_CLOSE,
                    ""
            ));
            return;
        }
        current = packet;
        DialogueStyleRenderer.open(packet);
    }

    public static void handleClose(PacketCloseDialogue packet) {
        clearSession(packet.sessionId());
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

    public static boolean trade(String offerId) {
        if (current == null || offerId == null || offerId.isBlank()) {
            return false;
        }
        NetworkHandler.sendToServer(new PacketShopTradeRequest(current.sessionId(), offerId));
        return true;
    }

    public static boolean close() {
        if (current == null) {
            return false;
        }
        UUID sessionId = current.sessionId();
        NetworkHandler.sendToServer(new PacketDialogueChoice(sessionId, PacketDialogueChoice.ACTION_CLOSE, ""));
        clearSession(sessionId);
        return true;
    }

    public static void reset() {
        current = null;
        DialogueStyleRenderer.clear();
    }

    private static void clearSession(UUID sessionId) {
        if (current != null && current.sessionId().equals(sessionId)) {
            current = null;
        }
        DialogueStyleRenderer.close(sessionId);
    }
}
