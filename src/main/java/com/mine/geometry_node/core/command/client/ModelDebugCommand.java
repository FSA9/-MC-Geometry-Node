package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.model.debug.ModelDebugHud;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

public final class ModelDebugCommand {
    private ModelDebugCommand() {}

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("geometry_node")
                .then(LiteralArgumentBuilder.<S>literal("model_debug")
                        .then(LiteralArgumentBuilder.<S>literal("on")
                                .executes(context -> setEnabled(true)))
                        .then(LiteralArgumentBuilder.<S>literal("off")
                                .executes(context -> setEnabled(false)))));
    }

    private static int setEnabled(boolean enabled) {
        ModelDebugHud.setEnabled(enabled);
        ClientCommandUtils.sendClientMsg(enabled
                ? "§aModel debug HUD enabled."
                : "§eModel debug HUD disabled.");
        return 1;
    }
}
