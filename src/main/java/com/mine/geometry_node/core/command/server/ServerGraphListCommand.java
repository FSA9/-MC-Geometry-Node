package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class ServerGraphListCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("graph_list")
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_MODERATOR))
                        .then(Commands.literal("server")
                                .executes(context -> {
                                    Set<String> dynamicGraphs = DynamicGraphManager.getAllDynamicGraphIds();
                                    Set<String> datapackGraphs = GraphResourceManager.getInstance().getAllGraphIds();
                                    CommandSourceStack source = context.getSource();

                                    if (dynamicGraphs.isEmpty()) {
                                        source.sendSuccess(() -> Component.translatable("geometry_node.command.graph_list.dynamic_empty"), false);
                                    } else {
                                        String list = String.join("\n- ", dynamicGraphs);
                                        source.sendSuccess(() -> Component.translatable("geometry_node.command.graph_list.dynamic", list), false);
                                    }

                                    if (datapackGraphs.isEmpty()) {
                                        source.sendSuccess(() -> Component.translatable("geometry_node.command.graph_list.datapack_empty"), false);
                                    } else {
                                        String list = String.join("\n- ", datapackGraphs);
                                        source.sendSuccess(() -> Component.translatable("geometry_node.command.graph_list.datapack", list), false);
                                    }

                                    return dynamicGraphs.size() + datapackGraphs.size();
                                })
                        )
        );
    }
}
