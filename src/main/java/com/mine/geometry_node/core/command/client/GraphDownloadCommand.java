package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferRequest;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;
import com.mine.geometry_node.core.engine.system.asset.transfer.model.AssetTransferConflictPolicy;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import java.util.List;
import java.util.UUID;
import java.util.Locale;

public final class GraphDownloadCommand {
    private GraphDownloadCommand() {
    }

    public static <S> void register(CommandDispatcher<S> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<S>literal("graph_download")
                .then(RequiredArgumentBuilder.<S, String>argument("graph_id", StringArgumentType.greedyString())
                        .suggests((context, builder) -> {
                            String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                            for (String graphPath : RemoteAssetClient.knownGraphPaths()) {
                                if (graphPath.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                    builder.suggest(graphPath);
                                }
                            }
                            return builder.buildFuture();
                        })
                        .executes(context -> {
                            String graphId = GraphPathMapper.normalizeId(
                                    StringArgumentType.getString(context, "graph_id"));
                            UUID jobId = ClientAssetTransferService.INSTANCE.submit(List.of(
                                    ClientAssetTransferRequest.download(graphId,
                                            LocalDraftManager.resolveDraftPath(graphId),
                                            AssetTransferConflictPolicy.OVERWRITE)));
                            ClientCommandUtils.sendClientMsg("§a已加入下载队列: " + graphId);
                            ClientAssetTransferService.INSTANCE.completion(jobId).thenAccept(result -> {
                                if (result.completedFileCount() == 1) {
                                    ClientCommandUtils.sendClientMsg("§a下载完成: " + graphId);
                                } else {
                                    ClientCommandUtils.sendClientMsg("§c下载失败: " + graphId);
                                }
                            });
                            return 1;
                        })));
    }
}
