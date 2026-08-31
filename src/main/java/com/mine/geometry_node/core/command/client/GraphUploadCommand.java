package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mine.geometry_node.core.engine.system.asset.AssetTypeCatalog;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.List;
import java.util.UUID;

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
                            List<ClientAssetTransferRequest> requests = drafts.stream()
                                    .map(GraphUploadCommand::uploadRequest)
                                    .toList();
                            submit(requests, "上传");
                            return requests.size();
                        })
                        .then(RequiredArgumentBuilder.<S, String>argument("graph_name", StringArgumentType.greedyString())
                                .suggests(ClientCommandUtils.getLocalSuggestions())
                                .executes(context -> {
                                    String name = StringArgumentType.getString(context, "graph_name");
                                    var path = LocalDraftManager.resolveDraftPath(name);
                                    if (AssetTypeCatalog.isType(path, AssetTypeCatalog.GRAPH_TYPE_ID)) {
                                        submit(List.of(uploadRequest(name)), "上传");
                                    } else {
                                        ClientCommandUtils.sendClientMsg("§c找不到有效图文件: " + name);
                                    }
                                    return 1;
                                })
                        )
        );
    }

    private static ClientAssetTransferRequest uploadRequest(String graphId) {
        return ClientAssetTransferRequest.upload(LocalDraftManager.resolveDraftPath(graphId), graphId,
                AssetTransferConflictPolicy.OVERWRITE);
    }

    private static void submit(List<ClientAssetTransferRequest> requests, String action) {
        UUID jobId = ClientAssetTransferService.INSTANCE.submit(requests);
        ClientCommandUtils.sendClientMsg("§a已将 " + requests.size() + " 个图加入" + action + "队列。");
        ClientAssetTransferService.INSTANCE.completion(jobId).thenAccept(result -> {
            long completed = result.completedFileCount();
            long failed = result.files().size() - completed;
            String color = failed == 0 ? "§a" : "§e";
            ClientCommandUtils.sendClientMsg(color + action + "完成: " + completed + " 成功, " + failed + " 失败。");
        });
    }
}
