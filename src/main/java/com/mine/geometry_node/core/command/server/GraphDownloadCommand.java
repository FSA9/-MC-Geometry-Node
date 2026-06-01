package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.execution.storage.DynamicGraphManager;
import com.mine.geometry_node.core.execution.storage.GraphIdMapper;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncDownload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

public class GraphDownloadCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("graph_download")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                .suggests(ServerCommandUtils.SUGGEST_GRAPHS)
                                .executes(context -> {
                                    ServerPlayer player = context.getSource().getPlayerOrException();
                                    String graphId = StringArgumentType.getString(context, "graph_id");

                                    try {
                                        Path folder = player.getServer().getWorldPath(DynamicGraphManager.GRAPH_DIR);
                                        File file = GraphIdMapper.resolveGraphPath(folder, graphId).toFile();

                                        if (file.exists()) {
                                            String json = Files.readString(file.toPath());
                                            NetworkHandler.sendToPlayer(player, new PacketSyncDownload(graphId, json));
                                            context.getSource().sendSuccess(() -> Component.literal("§a正在下发图纸: " + graphId), false);
                                        } else {
                                            context.getSource().sendFailure(Component.literal("找不到动态图纸文件，可能是内置数据包图纸，或路径错误。"));
                                        }
                                    } catch (Exception e) {
                                        context.getSource().sendFailure(Component.literal("服务器读取文件失败: " + e.getMessage()));
                                    }
                                    return 1;
                                })
                        )
        );
    }
}
