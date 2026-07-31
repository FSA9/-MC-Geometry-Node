package com.mine.geometry_node.core.engine.dialogue.presenter;

import com.mine.geometry_node.core.engine.dialogue.DialogueSession;
import net.minecraft.server.level.ServerPlayer;

public interface DialoguePresenter {
    String id();

    void open(ServerPlayer player, DialogueSession session);

    default void close(ServerPlayer player, DialogueSession session, String reason) {
    }
}
