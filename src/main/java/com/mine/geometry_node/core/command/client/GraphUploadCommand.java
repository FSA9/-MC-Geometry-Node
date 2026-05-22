package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketSyncUpload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.List;

public class GraphUploadCommand {
    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("graph_upload")
                        .executes(context -> {
                            List<String> drafts = LocalDraftManager.getAllDraftNames();
                            if (drafts.isEmpty()) {
                                ClientCommandUtils.sendClientMsg("§c没有可以上传的本地草稿。");
                                return 0;
                            }
                            int count = 0;
                            for (String name : drafts) {
                                String json = LocalDraftManager.readDraft(name);
                                if (json != null) {
                                    NetworkHandler.sendToServer(new PacketSyncUpload(name, json));
                                    count++;
                                }
                            }
                            ClientCommandUtils.sendClientMsg("§a已将 " + count + " 个本地图纸发送至服务器队列！");
                            return count;
                        })
                        .then(RequiredArgumentBuilder.<S, String>argument("graph_name", StringArgumentType.greedyString())
                                .suggests(ClientCommandUtils.getLocalSuggestions())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "graph_name");
                                    String json = LocalDraftManager.readDraft(name);
                                    if (json != null) {
                                        NetworkHandler.sendToServer(new PacketSyncUpload(name, json));
                                        ClientCommandUtils.sendClientMsg("§a正在上传本地图纸: " + name);
                                    } else {
                                        ClientCommandUtils.sendClientMsg("§c找不到名为 " + name + " 的本地草稿！");
                                    }
                                    return 1;
                                })
                        )
        );
    }
}