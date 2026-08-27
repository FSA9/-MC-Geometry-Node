package com.mine.geometry_node.client.runtime.dialogue;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketDialogueChoice;
import com.mine.geometry_node.core.network.packet.c2s.PacketShopTradeRequest;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public final class ClientDialogueState {
    @Nullable
    private static volatile PacketOpenDialogue current;
    @Nullable
    private static volatile PreviewSession previewSession;

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
        previewSession = null;
        current = packet;
        DialogueStyleRenderer.open(packet);
    }

    public static boolean openPreview(List<PacketOpenDialogue> pages) {
        if (pages == null || pages.isEmpty()) {
            return false;
        }
        for (PacketOpenDialogue page : pages) {
            if (page == null || !DialogueStyleRenderer.supports(page.styleId())) {
                return false;
            }
        }
        List<PacketOpenDialogue> safePages = List.copyOf(pages);
        previewSession = new PreviewSession(safePages);
        current = previewSession.current();
        DialogueStyleRenderer.open(current);
        return true;
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
        if (previewSession != null) {
            if (!previewSession.advance(choiceId)) {
                return false;
            }
            current = previewSession.current();
            DialogueStyleRenderer.open(current);
            return true;
        }
        UUID sessionId = current.sessionId();
        NetworkHandler.sendToServer(new PacketDialogueChoice(sessionId, PacketDialogueChoice.ACTION_CHOOSE, choiceId));
        return true;
    }

    public static boolean trade(String offerId) {
        if (current == null || offerId == null || offerId.isBlank()) {
            return false;
        }
        if (previewSession != null) {
            return false;
        }
        NetworkHandler.sendToServer(new PacketShopTradeRequest(current.sessionId(), offerId));
        return true;
    }

    public static boolean close() {
        if (current == null) {
            return false;
        }
        if (previewSession != null) {
            previewSession = null;
            current = null;
            DialogueStyleRenderer.clear();
            return true;
        }
        UUID sessionId = current.sessionId();
        NetworkHandler.sendToServer(new PacketDialogueChoice(sessionId, PacketDialogueChoice.ACTION_CLOSE, ""));
        clearSession(sessionId);
        return true;
    }

    public static void reset() {
        previewSession = null;
        current = null;
        DialogueStyleRenderer.clear();
    }

    private static void clearSession(UUID sessionId) {
        if (current != null && current.sessionId().equals(sessionId)) {
            current = null;
        }
        DialogueStyleRenderer.close(sessionId);
    }

    private static final class PreviewSession {
        private final List<PacketOpenDialogue> pages;
        private int index;

        private PreviewSession(List<PacketOpenDialogue> pages) {
            this.pages = pages;
        }

        private PacketOpenDialogue current() {
            return pages.get(index);
        }

        private boolean advance(String choiceId) {
            boolean enabledChoice = current().choices().stream()
                    .anyMatch(choice -> choice.enabled() && choice.choiceId().equals(choiceId));
            if (!enabledChoice || index + 1 >= pages.size()) {
                return false;
            }
            index++;
            return true;
        }
    }
}
