package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.execution.storage.DynamicGraphManager;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Set;

public class ServerGraphListCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("graph_list")
                        .requires(source -> source.hasPermission(1))
                        .then(Commands.literal("server")
                                .executes(context -> {
                                    Set<String> dynamicGraphs = DynamicGraphManager.getAllDynamicGraphIds();
                                    Set<String> datapackGraphs = GraphResourceManager.getInstance().getAllGraphIds();
                                    CommandSourceStack source = context.getSource();

                                    if (dynamicGraphs.isEmpty()) {
                                        source.sendSuccess(() -> Component.literal("§e[云端图纸 - 动态上传]§r 无"), false);
                                    } else {
                                        String list = String.join("\n- ", dynamicGraphs);
                                        source.sendSuccess(() -> Component.literal("§a[云端图纸 - 动态上传]§r\n- " + list), false);
                                    }

                                    if (datapackGraphs.isEmpty()) {
                                        source.sendSuccess(() -> Component.literal("§e[云端图纸 - 数据包内置]§r 无"), false);
                                    } else {
                                        String list = String.join("\n- ", datapackGraphs);
                                        source.sendSuccess(() -> Component.literal("§b[云端图纸 - 数据包内置]§r\n- " + list), false);
                                    }

                                    return dynamicGraphs.size() + datapackGraphs.size();
                                })
                        )
        );
    }
}
