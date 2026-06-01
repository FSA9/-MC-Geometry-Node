package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import java.util.List;

public class ClientGraphListCommand {
    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(
                LiteralArgumentBuilder.<S>literal("graph_list")
                        .then(LiteralArgumentBuilder.<S>literal("client")
                                .executes(context -> {
                                    List<String> drafts = LocalDraftManager.getAllDraftNames();
                                    String list = drafts.isEmpty() ? "无" : String.join("\n- ", drafts);
                                    ClientCommandUtils.sendClientMsg("§e[本地草稿列表]§r\n- " + list);
                                    return 1;
                                })
                        )
        );
    }
}