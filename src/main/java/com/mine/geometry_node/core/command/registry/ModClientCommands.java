package com.mine.geometry_node.core.command.registry;

import com.mine.geometry_node.core.command.client.ClientGraphListCommand;
import com.mine.geometry_node.core.command.client.GraphUploadCommand;
import com.mine.geometry_node.core.command.client.LocalModelPreviewCommand;
import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;

public class ModClientCommands {
    public static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, buildContext) -> {
            registerClientCommands(dispatcher);
        });
    }

    private static <S> void registerClientCommands(CommandDispatcher<S> dispatcher) {
        ClientGraphListCommand.register(dispatcher);
        GraphUploadCommand.register(dispatcher);
        LocalModelPreviewCommand.register(dispatcher);
    }
}
