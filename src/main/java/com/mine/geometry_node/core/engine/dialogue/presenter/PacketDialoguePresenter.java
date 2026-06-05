package com.mine.geometry_node.core.engine.dialogue.presenter;

import com.mine.geometry_node.core.engine.dialogue.session.DialogueSession;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import net.minecraft.server.level.ServerPlayer;

public final class PacketDialoguePresenter implements DialoguePresenter {
    public static final PacketDialoguePresenter INSTANCE = new PacketDialoguePresenter();

    private PacketDialoguePresenter() {
    }

    @Override
    public String id() {
        return "packet";
    }

    @Override
    public void open(ServerPlayer player, DialogueSession session) {
        NetworkHandler.sendToPlayer(player, PacketOpenDialogue.from(session));
    }

    @Override
    public void close(ServerPlayer player, DialogueSession session, String reason) {
        NetworkHandler.sendToPlayer(player, new PacketCloseDialogue(session.getSessionId(), reason));
    }
}
