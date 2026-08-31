package com.mine.geometry_node.core.network;

import com.mine.geometry_node.core.node.value.entity.EntityTemplateTargetResolvers;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugService;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetEntry;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetFileService;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.transfer.service.ServerAssetTransferService;
import com.mine.geometry_node.core.engine.system.asset.preview.ServerAssetPreviewService;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferAck;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferCancel;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferChunk;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferComplete;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferOpen;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferPlanRequest;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferResult;
import com.mine.geometry_node.core.engine.system.quest.QuestScreenService;
import com.mine.geometry_node.core.network.packet.c2s.PacketCaptureEntityTemplateRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketBehaviorDebugSubscription;
import com.mine.geometry_node.core.network.packet.c2s.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketAssetPreviewCancel;
import com.mine.geometry_node.core.network.packet.c2s.PacketDialogueChoice;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import com.mine.geometry_node.core.network.packet.c2s.PacketQuestScreenAction;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphCapabilitiesRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphFileOperationRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteGraphListRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketShopTradeRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketSyncUpload;
import com.mine.geometry_node.core.network.packet.s2c.PacketCaptureEntityTemplateResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphFileOperationResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphListResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.Collections;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.nio.file.Files;
import java.util.Set;
import java.util.WeakHashMap;

public class NetworkHandler {
    private static final Map<ServerPlayer, Set<String>> SENT_VISUAL_ASSETS =
            Collections.synchronizedMap(new WeakHashMap<>());

    public static void init() {
        ClientboundPayloadRegistry.registerDedicatedServerTypes();
        GraphEngineServices.INSTANCE.setVisualSink(NetworkHandler::broadcastVisualEffect);
        ServerAssetTransferService.INSTANCE.init();
        ServerAssetPreviewService.INSTANCE.init();
        BehaviorTreeDebugService.INSTANCE.init();

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketBehaviorDebugSubscription.TYPE,
                PacketBehaviorDebugSubscription.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        BehaviorTreeDebugService.INSTANCE.handle(
                                player, payload.instanceId(), payload.subscribe());
                    }
                }));

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetPreviewRequest.TYPE,
                PacketAssetPreviewRequest.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) ServerAssetPreviewService.INSTANCE.handleRequest(player, payload);
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetPreviewCancel.TYPE,
                PacketAssetPreviewCancel.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) ServerAssetPreviewService.INSTANCE.cancel(player, payload.requestId());
                }));

        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferOpen.TYPE,
                PacketAssetTransferOpen.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handleOpen(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferPlanRequest.TYPE,
                PacketAssetTransferPlanRequest.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handlePlan(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferChunk.TYPE,
                PacketAssetTransferChunk.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handleChunk(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferAck.TYPE,
                PacketAssetTransferAck.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handleAck(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferComplete.TYPE,
                PacketAssetTransferComplete.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handleComplete(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferResult.TYPE,
                PacketAssetTransferResult.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handleResult(player, payload);
                    }
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.C2S, PacketAssetTransferCancel.TYPE,
                PacketAssetTransferCancel.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        ServerAssetTransferService.INSTANCE.handleCancel(player, payload);
                    }
                }));

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
                                if (!RemoteAssetPermissions.canUploadAssets(player)) {
                                    sendToPlayer(player, new PacketSyncResponse(false, graphId, "没有上传服务器图纸的权限。"));
                                    return;
                                }
                                DynamicGraphManager.saveAndHotReload(server, graphId, jsonContent);
                                sendToPlayer(player, new PacketSyncResponse(true, graphId, "上传成功！"));
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
                                    RemoteAssetPermissions.canBrowseRemoteAssets(player),
                                    RemoteAssetPermissions.canUploadAssets(player),
                                    RemoteAssetPermissions.canDownloadAssets(player),
                                    RemoteAssetPermissions.canManageAssets(player)
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
                        if (!RemoteAssetPermissions.canBrowseRemoteAssets(player)) {
                            sendToPlayer(player, new PacketRemoteGraphListResponse(
                                    payload.requestId(), false, payload.directory(), "没有浏览服务器图纸的权限。", Collections.emptyList()));
                            return;
                        }
                        try {
                            String directory = RemoteAssetFileService.normalizeDirectoryPath(payload.directory());
                            if (payload.createIfMissing()) {
                                if (!RemoteAssetPermissions.canCreateRemoteFolders(player)) {
                                    sendToPlayer(player, new PacketRemoteGraphListResponse(
                                            payload.requestId(), false, payload.directory(), "没有创建服务器图纸文件夹的权限。", Collections.emptyList()));
                                    return;
                                }
                                Files.createDirectories(RemoteAssetFileService.resolveDirectory(player.level().getServer(), directory));
                            }
                            List<RemoteAssetEntry> entries = RemoteAssetFileService.list(player.level().getServer(), directory);
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
                            com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime.INSTANCE.handlePlayerInput(
                                    player, payload.keyId(), payload.action(), payload.clientVelocity());
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

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketQuestScreenAction.TYPE,
                PacketQuestScreenAction.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        QuestScreenService.INSTANCE.handleAction(player, payload);
                    }
                })
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketCaptureEntityTemplateRequest.TYPE,
                PacketCaptureEntityTemplateRequest.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        handleEntityTemplateCapture(payload, player);
                    }
                })
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

        Map<String, ExpressionData> expressions = effect.expressions() != null
                ? effect.expressions()
                : Collections.emptyMap();
        net.minecraft.nbt.CompoundTag extraData = effect.extraData() != null
                ? effect.extraData()
                : new net.minecraft.nbt.CompoundTag();

        PacketSpawnDynamicVisual packet = new PacketSpawnDynamicVisual(
                effect.effectType(),
                effect.color(),
                effect.durationTicks(),
                expressions,
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
            List<GraphEngineServices.VisualAsset> assets = effect.assets() != null
                    ? effect.assets()
                    : Collections.emptyList();
            for (GraphEngineServices.VisualAsset asset : assets) {
                if (asset == null || asset.data().length == 0) continue;
                PacketVisualAssetData assetPacket = new PacketVisualAssetData(asset.assetId(), asset.data());
                for (ServerPlayer player : targetPlayers) {
                    if (markVisualAssetSent(player, asset.assetId())) {
                        sendToPlayer(player, assetPacket);
                    }
                }
            }
            sendToPlayers(targetPlayers, packet);
        }
    }

    private static boolean markVisualAssetSent(ServerPlayer player, String assetId) {
        synchronized (SENT_VISUAL_ASSETS) {
            return SENT_VISUAL_ASSETS.computeIfAbsent(player, ignored -> new HashSet<>()).add(assetId);
        }
    }

    private static void handleEntityTemplateCapture(PacketCaptureEntityTemplateRequest payload, ServerPlayer player) {
        Entity selected = player.level().getEntityOrPart(payload.entityId());
        if (selected == null || selected.isRemoved()) {
            sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.not_found");
            return;
        }
        if (!player.isWithinEntityInteractionRange(selected, 1.0)) {
            sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.too_far");
            return;
        }

        Entity entity = EntityTemplateTargetResolvers.resolve(selected);
        if (entity.isRemoved()) {
            sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.not_found");
            return;
        }
        if (!entity.getType().canSerialize() || !entity.getType().canSummon()) {
            sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.unsupported");
            return;
        }

        try {
            EntityTemplateValue template = EntityTemplateValue.capture(entity);
            if (template.isEmpty()) {
                sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.failed");
                return;
            }
            if (template.toJsonString().length() > 1_000_000) {
                sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.too_large");
                return;
            }
            sendToPlayer(player, new PacketCaptureEntityTemplateResponse(
                    payload.requestId(),
                    true,
                    template.entityTypeId(),
                    template.data(),
                    ""
            ));
        } catch (RuntimeException exception) {
            sendEntityTemplateCaptureFailure(player, payload.requestId(), "geometry_node.entity_template.capture.failed");
        }
    }

    private static void sendEntityTemplateCaptureFailure(ServerPlayer player, int requestId, String messageKey) {
        sendToPlayer(player, new PacketCaptureEntityTemplateResponse(
                requestId,
                false,
                "",
                new net.minecraft.nbt.CompoundTag(),
                messageKey
        ));
    }

    private static void handleRemoteGraphFileOperation(PacketRemoteGraphFileOperationRequest payload, ServerPlayer player) {
        if (!RemoteAssetPermissions.canManageAssets(player)) {
            sendToPlayer(player, new PacketRemoteGraphFileOperationResponse(
                    payload.requestId(), false, "没有管理服务器图纸文件的权限。"));
            return;
        }

        try {
            MinecraftServer server = player.level().getServer();
            int count;
            try {
                count = switch (payload.operation()) {
                    case DELETE -> RemoteAssetFileService.deleteSelection(server, payload.paths());
                    case COPY -> RemoteAssetFileService.copySelection(
                            server, payload.paths(), payload.targetDirectory());
                    case MOVE -> RemoteAssetFileService.moveSelection(
                            server, payload.paths(), payload.targetDirectory());
                };
            } catch (Exception operationException) {
                try {
                    DynamicGraphManager.loadAllFromDisk(server);
                } catch (RuntimeException reloadException) {
                    operationException.addSuppressed(reloadException);
                }
                throw operationException;
            }
            DynamicGraphManager.loadAllFromDisk(server);
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
