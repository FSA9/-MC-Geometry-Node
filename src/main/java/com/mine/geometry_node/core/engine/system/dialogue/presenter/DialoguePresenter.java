package com.mine.geometry_node.core.engine.system.dialogue.presenter;

import com.mine.geometry_node.core.engine.system.dialogue.DialogueSession;
import net.minecraft.server.level.ServerPlayer;

public interface DialoguePresenter {
    String id();

    void open(ServerPlayer player, DialogueSession session);

    default void close(ServerPlayer player, DialogueSession session, String reason) {
    }
}
