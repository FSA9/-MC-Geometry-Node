package com.mine.geometry_node.core.network;

import com.mine.geometry_node.core.node.value.entity.EntityTemplateTargetResolvers;
import com.mine.geometry_node.core.engine.behavior.debug.BehaviorTreeDebugService;
import com.mine.geometry_node.core.engine.system.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.service.GraphEngineServices;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionData;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetPermissions;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetOperationResult;
import com.mine.geometry_node.core.engine.system.asset.AssetLifecycleRegistry;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetRepositoryService;
import com.mine.geometry_node.core.engine.system.asset.transfer.service.ServerAssetTransferService;
import com.mine.geometry_node.core.engine.system.asset.preview.ServerAssetPreviewService;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferAck;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferCancel;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferChunk;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferComplete;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferOpen;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanRequest;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferResult;
import com.mine.geometry_node.core.engine.system.quest.QuestScreenService;
import com.mine.geometry_node.core.network.packet.c2s.PacketCaptureEntityTemplateRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketBehaviorDebugSubscription;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewRequest;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewCancel;
import com.mine.geometry_node.core.network.packet.c2s.PacketDialogueChoice;
import com.mine.geometry_node.core.network.packet.c2s.PacketPlayerInput;
import com.mine.geometry_node.core.network.packet.c2s.PacketQuestScreenAction;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesRequest;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationRequest;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListRequest;
import com.mine.geometry_node.core.network.packet.c2s.PacketShopTradeRequest;
import com.mine.geometry_node.core.network.packet.s2c.PacketCaptureEntityTemplateResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import com.mine.geometry_node.core.engine.system.data.library.RemoteDataLibraryTransferStaging;
import com.mine.geometry_node.core.engine.system.data.library.RemoteDataLibraryService;
import com.mine.geometry_node.core.network.packet.data.library.PacketRemoteDataLibraryRequest;
import com.mine.geometry_node.core.network.packet.data.library.PacketRemoteDataLibraryResponse;
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
        RemoteDataLibraryService.INSTANCE.init();
        RemoteDataLibraryTransferStaging.INSTANCE.init();
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

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteAssetCapabilitiesRequest.TYPE,
                PacketRemoteAssetCapabilitiesRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            sendToPlayer(player, new PacketRemoteAssetCapabilitiesResponse(
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
                PacketRemoteAssetListRequest.TYPE,
                PacketRemoteAssetListRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (!(context.getPlayer() instanceof ServerPlayer player)) return;
                        if (!RemoteAssetPermissions.canBrowseRemoteAssets(player)) {
                            sendToPlayer(player, new PacketRemoteAssetListResponse(
                                    payload.requestId(), false, payload.directory(), "没有浏览服务器资产的权限。", Collections.emptyList()));
                            return;
                        }
                        if (payload.createIfMissing() && !RemoteAssetPermissions.canCreateRemoteFolders(player)) {
                            sendToPlayer(player, new PacketRemoteAssetListResponse(
                                    payload.requestId(), false, payload.directory(),
                                    "没有创建服务器资产文件夹的权限。", Collections.emptyList()));
                            return;
                        }
                        MinecraftServer server = player.level().getServer();
                        try {
                            RemoteAssetRepositoryService.INSTANCE.list(
                                            server, payload.directory(), payload.createIfMissing())
                                    .whenComplete((result, error) -> server.execute(() -> {
                                        if (error != null) {
                                            sendToPlayer(player, new PacketRemoteAssetListResponse(
                                                    payload.requestId(), false, payload.directory(),
                                                    failureMessage(error), Collections.emptyList()));
                                            return;
                                        }
                                        sendToPlayer(player, new PacketRemoteAssetListResponse(
                                                payload.requestId(), true, result.directory(), "", result.entries()));
                                    }));
                        } catch (RuntimeException exception) {
                            sendToPlayer(player, new PacketRemoteAssetListResponse(
                                    payload.requestId(), false, payload.directory(),
                                    failureMessage(exception), Collections.emptyList()));
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteAssetFileOperationRequest.TYPE,
                PacketRemoteAssetFileOperationRequest.STREAM_CODEC,
                (payload, context) -> {
                    context.queue(() -> {
                        if (context.getPlayer() instanceof ServerPlayer player) {
                            handleRemoteAssetFileOperation(payload, player);
                        }
                    });
                }
        );

        NetworkManager.registerReceiver(
                NetworkManager.Side.C2S,
                PacketRemoteDataLibraryRequest.TYPE,
                PacketRemoteDataLibraryRequest.STREAM_CODEC,
                (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() instanceof ServerPlayer player) {
                        handleRemoteDataLibrary(payload, player);
                    }
                })
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

    private static void handleRemoteAssetFileOperation(PacketRemoteAssetFileOperationRequest payload, ServerPlayer player) {
        if (!RemoteAssetPermissions.canManageAssets(player)) {
            sendToPlayer(player, new PacketRemoteAssetFileOperationResponse(
                    payload.requestId(), false, "没有管理服务器资产文件的权限。"));
            return;
        }

        MinecraftServer server = player.level().getServer();
        try {
            java.util.concurrent.CompletableFuture<RemoteAssetOperationResult> operation = switch (payload.operation()) {
                case DELETE -> RemoteAssetRepositoryService.INSTANCE.delete(server, payload.paths());
                case COPY -> RemoteAssetRepositoryService.INSTANCE.copy(
                        server, payload.paths(), payload.destinationPath());
                case MOVE -> RemoteAssetRepositoryService.INSTANCE.move(
                        server, payload.paths(), payload.destinationPath());
                case CREATE_DIRECTORY -> RemoteAssetRepositoryService.INSTANCE.createDirectory(
                        server, payload.destinationPath());
                case RENAME -> {
                    if (payload.paths().size() != 1) {
                        throw new IllegalArgumentException("Rename requires exactly one source path");
                    }
                    yield RemoteAssetRepositoryService.INSTANCE.rename(
                            server, payload.paths().getFirst(), payload.destinationPath());
                }
            };
            operation.whenComplete((result, error) -> server.execute(() -> {
                if (error != null) {
                    try {
                        AssetLifecycleRegistry.INSTANCE.refreshAll(server);
                    } catch (RuntimeException reloadException) {
                        error.addSuppressed(reloadException);
                    }
                    sendToPlayer(player, new PacketRemoteAssetFileOperationResponse(
                            payload.requestId(), false, "操作失败: " + failureMessage(error)));
                    return;
                }
                AssetLifecycleRegistry.INSTANCE.refresh(server, result.affectedTypeIds());
                String action = switch (payload.operation()) {
                    case DELETE -> "删除";
                    case COPY -> "复制";
                    case MOVE -> "移动";
                    case CREATE_DIRECTORY -> "新建文件夹";
                    case RENAME -> "重命名";
                };
                sendToPlayer(player, new PacketRemoteAssetFileOperationResponse(
                        payload.requestId(), true, action + "完成: " + result.affectedEntries()));
            }));
        } catch (RuntimeException exception) {
            sendToPlayer(player, new PacketRemoteAssetFileOperationResponse(
                    payload.requestId(), false, "操作失败: " + failureMessage(exception)));
        }
    }

    private static void handleRemoteDataLibrary(PacketRemoteDataLibraryRequest payload, ServerPlayer player) {
        try {
            var staging = RemoteDataLibraryTransferStaging.INSTANCE;
            switch (payload.operation()) {
                case PREPARE_REFRESH -> {
                    requireDataLibraryPermission(RemoteAssetPermissions.canBrowseRemoteAssets(player)
                            && RemoteAssetPermissions.canDownloadAssets(player));
                    MinecraftServer server = player.level().getServer();
                    staging.prepareDownloadAsync(player)
                            .whenComplete((ticket, error) -> server.execute(() -> sendToPlayer(player,
                                    error == null
                                            ? new PacketRemoteDataLibraryResponse(
                                                    payload.requestId(), true, "", ticket.token())
                                            : new PacketRemoteDataLibraryResponse(
                                                    payload.requestId(), false, failureMessage(error), ""))));
                }
                case DELETE -> {
                    requireDataLibraryPermission(RemoteAssetPermissions.canManageAssets(player));
                    MinecraftServer server = player.level().getServer();
                    staging.deleteAsync(player, payload.keys())
                            .whenComplete((ignored, error) -> server.execute(() -> sendToPlayer(player,
                                    error == null
                                            ? new PacketRemoteDataLibraryResponse(
                                                    payload.requestId(), true, "", "")
                                            : new PacketRemoteDataLibraryResponse(
                                                    payload.requestId(), false, failureMessage(error), ""))));
                }
            }
        } catch (Exception exception) {
            sendToPlayer(player, new PacketRemoteDataLibraryResponse(
                    payload.requestId(), false, failureMessage(exception), ""));
        }
    }

    private static void requireDataLibraryPermission(boolean allowed) {
        if (!allowed) throw new SecurityException("没有执行该 Data Library 操作的权限。");
    }

    private static String failureMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }
}
