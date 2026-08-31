package com.mine.geometry_node.core.command.registry;

import com.mine.geometry_node.core.command.server.GraphBindCommand;
import com.mine.geometry_node.core.command.server.BehaviorTreeCommand;
import com.mine.geometry_node.core.command.server.DialogueCommand;
import com.mine.geometry_node.core.command.server.GeometryNodeDebugCommand;
import com.mine.geometry_node.core.command.server.GraphUnbindCommand;
import com.mine.geometry_node.core.command.server.ServerGraphListCommand;
import com.mojang.brigadier.CommandDispatcher;
import dev.architectury.event.events.common.CommandRegistrationEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ModServerCommands {
    public static void register() {
        CommandRegistrationEvent.EVENT.register((dispatcher, registryAccess, environment) -> {
            GraphBindCommand.register(dispatcher);
            BehaviorTreeCommand.register(dispatcher);
            GraphUnbindCommand.register(dispatcher);
            ServerGraphListCommand.register(dispatcher);
            DialogueCommand.register(dispatcher);
            GeometryNodeDebugCommand.register(dispatcher);
        });
    }
}
