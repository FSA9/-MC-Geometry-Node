package com.mine.geometry_node.core.network;

import com.mine.geometry_node.client.render.ClientVisualManager;
import com.mine.geometry_node.client.render.debug.AreaDebugRenderer;
import com.mine.geometry_node.client.render.debug.GeometryDebugRenderer;
import com.mine.geometry_node.client.render.debug.SchematicProjectionRenderer;
import com.mine.geometry_node.client.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.ui.editor.asset.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mine.geometry_node.core.engine.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphConflict;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphEntry;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphFileService;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphPermissions;
import com.mine.geometry_node.core.engine.graph.storage.RemoteGraphUploadFile;
import com.mine.geometry_node.core.network.packet.c2s.*;
import com.mine.geometry_node.core.network.packet.s2c.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.util.Set;

public class NetworkHandler {

    public static void init() {
        GraphEngineServices.INSTANCE.setVisualSink(NetworkHandler::broadcastVisualEffect);

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSpawnDynamicVisual.TYPE,
                PacketSpawnDynamicVisual.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> ClientVisualManager.spawnEffectFromPacket(payload));
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketAreaDebugSnapshot.TYPE,
                PacketAreaDebugSnapshot.STREAM_CODEC,
                (payload, context) -> context.queue(() -> AreaDebugRenderer.handleSnapshot(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketGeometryDebugSnapshot.TYPE,
                PacketGeometryDebugSnapshot.STREAM_CODEC,
                (payload, context) -> context.queue(() -> GeometryDebugRenderer.handleSnapshot(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSchematicProjection.TYPE,
                PacketSchematicProjection.STREAM_CODEC,
                (payload, context) -> context.queue(() -> SchematicProjectionRenderer.handleProjection(payload))
        );

        // ==========================================
        // 1. 注册 C2S: 客户端上传蓝图 -> 服务端接收
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketSyncUpload.TYPE,
                PacketSyncUpload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            MinecraftServer server = player.level().getServer();
                            String graphId = payload.graphId();
                            String jsonContent = payload.jsonContent();

                            try {
                                if (!RemoteGraphPermissions.canUploadGraphs(player)) {
                                    sendToPlayer(player, new PacketSyncResponse(false, graphId, "没有上传服务器图纸的权限。"));
                                    return;
                                }
                                DynamicGraphManager.saveAndHotReload(server, graphId, jsonContent);
                                sendToPlayer(player, new PacketSyncResponse(true, graphId, "上传并热更新成功！"));
                            } catch (Exception e) {
                                sendToPlayer(player, new PacketSyncResponse(false, graphId, "上传失败: " + e.getMessage()));
                            }
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteGraphCapabilitiesRequest.TYPE,
                PacketRemoteGraphCapabilitiesRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            sendToPlayer(player, new PacketRemoteGraphCapabilitiesResponse(
                                    payload.requestId(),
                                    RemoteGraphPermissions.canBrowseRemoteGraphs(player),
                                    RemoteGraphPermissions.canUploadGraphs(player),
                                    RemoteGraphPermissions.canDownloadGraphs(player),
                                    RemoteGraphPermissions.canManageGraphs(player)
                            ));
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteGraphListRequest.TYPE,
                PacketRemoteGraphListRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                        if (!RemoteGraphPermissions.canBrowseRemoteGraphs(player)) {
                            sendToPlayer(player, new PacketRemoteGraphListResponse(
                                    payload.requestId(), false, payload.directory(), "没有浏览服务器图纸的权限。", Collections.emptyList()));
                            return;
                        }
                        try {
                            String directory = RemoteGraphFileService.normalizeDirectoryPath(payload.directory());
                            if (payload.createIfMissing()) {
                                if (!RemoteGraphPermissions.canCreateRemoteFolders(player)) {
                                    sendToPlayer(player, new PacketRemoteGraphListResponse(
                                            payload.requestId(), false, payload.directory(), "没有创建服务器图纸文件夹的权限。", Collections.emptyList()));
                                    return;
                                }
                                Files.createDirectories(RemoteGraphFileService.resolveDirectory(player.level().getServer(), directory));
                            }
                            List<RemoteGraphEntry> entries = RemoteGraphFileService.list(player.level().getServer(), directory);
                            sendToPlayer(player, new PacketRemoteGraphListResponse(payload.requestId(), true, directory, "", entries));
                        } catch (Exception e) {
                            sendToPlayer(player, new PacketRemoteGraphListResponse(
                                    payload.requestId(), false, payload.directory(), e.getMessage(), Collections.emptyList()));
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteGraphUploadRequest.TYPE,
                PacketRemoteGraphUploadRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            handleRemoteGraphUpload(payload, player);
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteGraphDownloadRequest.TYPE,
                PacketRemoteGraphDownloadRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            handleRemoteGraphDownload(payload, player);
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteGraphFileOperationRequest.TYPE,
                PacketRemoteGraphFileOperationRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            handleRemoteGraphFileOperation(payload, player);
                        }
                    });
                }
        );

        // ==========================================
        // 2. 注册 S2C: 服务端回执 -> 客户端接收
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSyncResponse.TYPE,
                PacketSyncResponse.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() != null) {
                            String prefix = payload.success() ? "§a[图纸同步成功]§r " : "§c[图纸同步失败]§r ";
                            context.getPlayer().sendSystemMessage(Component.literal(prefix + payload.graphId() + " - " + payload.message()));
                        }
                    });
                }
        );

        // ==========================================
        // 6. 注册 S2C: 服务端下发图纸内容 -> 客户端接收
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketSyncDownload.TYPE,
                PacketSyncDownload.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() != null) {
                            String graphId = payload.graphId();
                            String jsonContent = payload.jsonContent();

                            LocalDraftManager.saveDraft(graphId, jsonContent);

                            // 弹出提示
                            context.getPlayer().sendSystemMessage(Component.literal("§a[☁ 云端下载成功]§r 图纸 " + graphId + " 已保存到你的本地草稿箱！"));
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketRemoteGraphCapabilitiesResponse.TYPE,
                PacketRemoteGraphCapabilitiesResponse.STREAM_CODEC,
                (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketRemoteGraphListResponse.TYPE,
                PacketRemoteGraphListResponse.STREAM_CODEC,
                (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketRemoteGraphUploadResponse.TYPE,
                PacketRemoteGraphUploadResponse.STREAM_CODEC,
                (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketRemoteGraphDownloadResponse.TYPE,
                PacketRemoteGraphDownloadResponse.STREAM_CODEC,
                (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketRemoteGraphFileOperationResponse.TYPE,
                PacketRemoteGraphFileOperationResponse.STREAM_CODEC,
                (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload))
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketOpenDialogue.TYPE,
                PacketOpenDialogue.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        ClientDialogueState.handleOpen(payload);
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.S2C,
                PacketCloseDialogue.TYPE,
                PacketCloseDialogue.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        ClientDialogueState.handleClose(payload);
                    });
                }
        );

        // ==========================================
        // 7. 注册 C2S: 客户端按键输入 -> 服务端处理
        // ==========================================
        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketPlayerInput.TYPE,
                PacketPlayerInput.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            // 将数据包直接甩给状态管家处理
                            com.mine.geometry_node.core.engine.blueprint.event.PlayerInputStateManager.handleInput(player, payload);
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketDialogueChoice.TYPE,
                PacketDialogueChoice.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            if (PacketDialogueChoice.ACTION_CLOSE.equals(payload.action())) {
                                DialogueRuntime.INSTANCE.closeFromClient(player, payload.sessionId());
                            } else {
                                DialogueRuntime.INSTANCE.choose(player, payload.sessionId(), payload.choiceId());
                            }
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketShopTradeRequest.TYPE,
                PacketShopTradeRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            DialogueRuntime.INSTANCE.tradeShopOffer(player, payload.sessionId(), payload.offerId());
                        }
                    });
                }
        );
    }

    // ==========================================
    // 发包 API 工具方法
    // ==========================================
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        NetworkManager.sendToPlayer(player, payload);
    }

    public static void sendToPlayers(Iterable<ServerPlayer> players, CustomPacketPayload payload) {
        NetworkManager.sendToPlayers(players, payload);
    }

    public static void sendToServer(CustomPacketPayload payload) {
        NetworkManager.sendToServer(payload);
    }

    private static void broadcastVisualEffect(GraphEngineServices.VisualEffect effect) {
        if (effect == null || effect.level() == null || effect.center() == null || effect.radius() <= 0) {
            return;
        }

        Map<String, String> expressions = effect.expressions() != null
                ? effect.expressions()
                : Collections.emptyMap();
        Map<String, String> bindings = effect.bindings() != null
                ? effect.bindings()
                : Collections.emptyMap();
        net.minecraft.nbt.CompoundTag extraData = effect.extraData() != null
                ? effect.extraData()
                : new net.minecraft.nbt.CompoundTag();

        PacketSpawnDynamicVisual packet = new PacketSpawnDynamicVisual(
                effect.effectType(),
                effect.color(),
                effect.durationTicks(),
                expressions,
                bindings,
                extraData
        );

        double radiusSqr = effect.radius() * effect.radius();
        Vec3 center = effect.center();
        List<ServerPlayer> targetPlayers = new ArrayList<>();
        for (ServerPlayer player : effect.level().players()) {
            if (player.position().distanceToSqr(center) < radiusSqr) {
                targetPlayers.add(player);
            }
        }
        if (!targetPlayers.isEmpty()) {
            sendToPlayers(targetPlayers, packet);
        }
    }

    private static void handleRemoteGraphUpload(PacketRemoteGraphUploadRequest payload, ServerPlayer player) {
        if (!RemoteGraphPermissions.canUploadGraphs(player)) {
            sendToPlayer(player, new PacketRemoteGraphUploadResponse(
                    payload.requestId(), payload.preflightOnly(), false, true, 0, payload.files().size(),
                    "没有上传服务器图纸的权限。", Collections.emptyList()));
            return;
        }

        try {
            List<String> targetPaths = new ArrayList<>();
            for (RemoteGraphUploadFile file : payload.files()) {
                targetPaths.add(file.targetPath());
            }
            List<RemoteGraphConflict> conflicts = RemoteGraphFileService.findUploadConflicts(player.level().getServer(), targetPaths);
            if (payload.preflightOnly()) {
                sendToPlayer(player, new PacketRemoteGraphUploadResponse(
                        payload.requestId(), true, conflicts.isEmpty(), true,
                        0, payload.files().size(), "", conflicts));
                return;
            }
            Set<String> allowedOverwritePaths = new HashSet<>(payload.overwritePaths());
            if (!payload.overwrite()) {
                List<RemoteGraphConflict> blockingConflicts = new ArrayList<>();
                for (RemoteGraphConflict conflict : conflicts) {
                    if (!allowedOverwritePaths.contains(conflict.targetPath())) {
                        blockingConflicts.add(conflict);
                    }
                }
                if (!blockingConflicts.isEmpty()) {
                    sendToPlayer(player, new PacketRemoteGraphUploadResponse(
                            payload.requestId(), false, false, true,
                            0, payload.files().size(), "目标存在冲突。", blockingConflicts));
                    return;
                }
            }

            int processed = 0;
            int total = payload.files().size();
            for (RemoteGraphUploadFile file : payload.files()) {
                RemoteGraphFileService.saveUpload(
                        player.level().getServer(),
                        file,
                        payload.overwrite() || allowedOverwritePaths.contains(file.targetPath())
                );
                processed++;
                sendToPlayer(player, new PacketRemoteGraphUploadResponse(
                        payload.requestId(), false, true, false,
                        processed, total, "上传中", Collections.emptyList()));
            }
            sendToPlayer(player, new PacketRemoteGraphUploadResponse(
                    payload.requestId(), false, true, true,
                    processed, total, "上传完成", Collections.emptyList()));
        } catch (Exception e) {
            sendToPlayer(player, new PacketRemoteGraphUploadResponse(
                    payload.requestId(), false, false, true,
                    0, payload.files().size(), "上传失败: " + e.getMessage(), Collections.emptyList()));
        }
    }

    private static void handleRemoteGraphDownload(PacketRemoteGraphDownloadRequest payload, ServerPlayer player) {
        if (!RemoteGraphPermissions.canDownloadGraphs(player)) {
            sendToPlayer(player, new PacketRemoteGraphDownloadResponse(
                    payload.requestId(), false, true,
                    0, payload.paths().size(), "没有下载服务器图纸的权限。", Collections.emptyList()));
            return;
        }

        try {
            List<RemoteGraphEntry> files = RemoteGraphFileService.flattenSelection(player.level().getServer(), payload.paths());
            int total = files.size();
            int processed = 0;
            for (RemoteGraphEntry entry : files) {
                RemoteGraphUploadFile downloaded = new RemoteGraphUploadFile(
                        entry.path(),
                        RemoteGraphFileService.readGraph(player.level().getServer(), entry.path())
                );
                processed++;
                sendToPlayer(player, new PacketRemoteGraphDownloadResponse(
                        payload.requestId(), true, false, processed, total, "下载中", List.of(downloaded)));
            }
            sendToPlayer(player, new PacketRemoteGraphDownloadResponse(
                    payload.requestId(), true, true, processed, total, "下载完成", Collections.emptyList()));
        } catch (Exception e) {
            sendToPlayer(player, new PacketRemoteGraphDownloadResponse(
                    payload.requestId(), false, true,
                    0, payload.paths().size(), "下载失败: " + e.getMessage(), Collections.emptyList()));
        }
    }

    private static void handleRemoteGraphFileOperation(PacketRemoteGraphFileOperationRequest payload, ServerPlayer player) {
        if (!RemoteGraphPermissions.canManageGraphs(player)) {
            sendToPlayer(player, new PacketRemoteGraphFileOperationResponse(
                    payload.requestId(), false, "没有管理服务器图纸文件的权限。"));
            return;
        }

        try {
            int count = switch (payload.operation()) {
                case DELETE -> RemoteGraphFileService.deleteSelection(player.level().getServer(), payload.paths());
                case COPY -> RemoteGraphFileService.copySelection(player.level().getServer(), payload.paths(), payload.targetDirectory());
                case MOVE -> RemoteGraphFileService.moveSelection(player.level().getServer(), payload.paths(), payload.targetDirectory());
            };
            DynamicGraphManager.loadAllFromDisk(player.level().getServer());
            String action = switch (payload.operation()) {
                case DELETE -> "删除";
                case COPY -> "复制";
                case MOVE -> "移动";
            };
            sendToPlayer(player, new PacketRemoteGraphFileOperationResponse(
                    payload.requestId(), true, action + "完成: " + count));
        } catch (Exception e) {
            sendToPlayer(player, new PacketRemoteGraphFileOperationResponse(
                    payload.requestId(), false, "操作失败: " + e.getMessage()));
        }
    }
}
