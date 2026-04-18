package com.mine.geometry_node.core.command;

import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketSyncUpload;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.architectury.event.events.client.ClientCommandRegistrationEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ClientGraphCommand {

    public static void register() {
        ClientCommandRegistrationEvent.EVENT.register((dispatcher, buildContext) -> {
            buildAndRegister(dispatcher);
        });
    }

    private static <S> void buildAndRegister(CommandDispatcher<S> dispatcher) {
        // 1. 本地列表指令
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("graph_list")
                        .then(LiteralArgumentBuilder.<S>literal("client")
                                .executes(context -> {
                                    List<String> drafts = LocalDraftManager.getAllDraftNames();
                                    String list = drafts.isEmpty() ? "无" : String.join("\n- ", drafts);
                                    sendClientMsg("§e[本地草稿列表]§r\n- " + list);
                                    return 1;
                                })
                        )
        );

        // 2. 本地上传指令
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("graph_upload")
                        .executes(context -> {
                            // 批量上传所有本地图纸
                            List<String> drafts = LocalDraftManager.getAllDraftNames();
                            if (drafts.isEmpty()) {
                                sendClientMsg("§c没有可以上传的本地草稿。");
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
                            sendClientMsg("§a已将 " + count + " 个本地图纸发送至服务器队列！");
                            return count;
                        })
                        .then(RequiredArgumentBuilder.<S, String>argument("graph_name", StringArgumentType.greedyString())
                                .suggests(getLocalSuggestions())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "graph_name");
                                    String json = LocalDraftManager.readDraft(name);
                                    if (json != null) {
                                        NetworkHandler.sendToServer(new PacketSyncUpload(name, json));
                                        sendClientMsg("§a正在上传本地图纸: " + name);
                                    } else {
                                        sendClientMsg("§c找不到名为 " + name + " 的本地草稿！");
                                    }
                                    return 1;
                                })
                        )
        );
    }

    private static <S> SuggestionProvider<S> getLocalSuggestions() {
        return (context, builder) -> SharedSuggestionProvider.suggest(LocalDraftManager.getAllDraftNames(), builder);
    }

    // 辅助方法：只在客户端聊天框显示，不用通过网络发给服务端
    private static void sendClientMsg(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
        }
    }
}