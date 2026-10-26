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

    @Nullable
    private static volatile DialoguePresentation presentation;

    /** Install once for the client lifetime. Reinstalling the same instance is harmless, including during a session. */
    public static synchronized void installPresentation(DialoguePresentation value) {
        java.util.Objects.requireNonNull(value, "presentation");
        if (presentation != null && presentation != value) {
            throw new IllegalStateException("DialoguePresentation is already installed; replacing it would detach its screen lifecycle");
        }
        presentation = value;
    }

    private static DialoguePresentation presentation() {
        DialoguePresentation installed = presentation;
        if (installed == null) {
            throw new IllegalStateException("DialoguePresentation must be installed before receiving packets or opening previews");
        }
        return installed;
    }

    private ClientDialogueState() {
    }

    public static void handleOpen(PacketOpenDialogue packet) {
        if (!presentation().supports(packet.styleId())) {
            GeometryNode.LOGGER.warn(
                    "[ClientDialogueState] No renderer is registered for dialogue style '{}'; closing document {}.",
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
        presentation().open(packet);
    }

    public static boolean openPreview(List<PacketOpenDialogue> pages) {
        if (pages == null || pages.isEmpty()) {
            return false;
        }
        for (PacketOpenDialogue page : pages) {
            if (page == null || !presentation().supports(page.styleId())) {
                return false;
            }
        }
        List<PacketOpenDialogue> safePages = List.copyOf(pages);
        previewSession = new PreviewSession(safePages);
        current = previewSession.current();
        presentation().open(current);
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
            presentation().open(current);
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
        presentation();
        if (current == null) {
            return false;
        }
        if (previewSession != null) {
            previewSession = null;
            current = null;
            presentation().clear();
            return true;
        }
        UUID sessionId = current.sessionId();
        NetworkHandler.sendToServer(new PacketDialogueChoice(sessionId, PacketDialogueChoice.ACTION_CLOSE, ""));
        clearSession(sessionId);
        return true;
    }

    public static void reset() {
        presentation();
        previewSession = null;
        current = null;
        presentation().clear();
    }

    private static void clearSession(UUID sessionId) {
        DialoguePresentation installed = presentation();
        if (current != null && current.sessionId().equals(sessionId)) {
            current = null;
        }
        installed.close(sessionId);
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
