package com.mine.geometry_node.core.network;

import com.mine.geometry_node.client.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.marker.ClientMarkerStore;
import com.mine.geometry_node.client.quest.ClientQuestScreenState;
import com.mine.geometry_node.client.render.ClientVisualManager;
import com.mine.geometry_node.client.render.debug.GeometryDebugRenderer;
import com.mine.geometry_node.client.render.debug.SchematicProjectionRenderer;
import com.mine.geometry_node.client.render.image.ClientImageAssetManager;
import com.mine.geometry_node.client.ui.editor.asset.remote.RemoteGraphClientState;
import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.overlays.EntityTemplatePickerController;
import com.mine.geometry_node.core.network.packet.s2c.PacketCaptureEntityTemplateResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerRemove;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerUpsert;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphDownloadResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphFileOperationResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphListResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteGraphUploadResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncDownload;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import dev.architectury.networking.NetworkManager;
import net.minecraft.network.chat.Component;

/** Registers S2C receivers and routes their payloads into client-owned state. */
public final class ClientNetworkReceiverRegistry {
    private static boolean initialized;

    private ClientNetworkReceiverRegistry() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketSpawnDynamicVisual.TYPE,
                PacketSpawnDynamicVisual.STREAM_CODEC,
                (payload, context) -> context.queue(() -> ClientVisualManager.spawnEffectFromPacket(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketVisualAssetData.TYPE,
                PacketVisualAssetData.STREAM_CODEC,
                (payload, context) -> context.queue(() -> ClientImageAssetManager.acceptServerAsset(payload.assetId(), payload.data())));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketGeometryDebugSnapshot.TYPE,
                PacketGeometryDebugSnapshot.STREAM_CODEC,
                (payload, context) -> context.queue(() -> GeometryDebugRenderer.handleSnapshot(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketSchematicProjection.TYPE,
                PacketSchematicProjection.STREAM_CODEC,
                (payload, context) -> context.queue(() -> SchematicProjectionRenderer.handleProjection(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketSyncResponse.TYPE,
                PacketSyncResponse.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() == null) return;
                    String prefix = payload.success() ? "§a[图纸同步成功]§r " : "§c[图纸同步失败]§r ";
                    context.getPlayer().sendSystemMessage(Component.literal(prefix + payload.graphId() + " - " + payload.message()));
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketSyncDownload.TYPE,
                PacketSyncDownload.STREAM_CODEC, (payload, context) -> context.queue(() -> {
                    if (context.getPlayer() == null) return;
                    LocalDraftManager.saveDraft(payload.graphId(), payload.jsonContent());
                    context.getPlayer().sendSystemMessage(Component.literal(
                            "§a[☁ 云端下载成功]§r 图纸 " + payload.graphId() + " 已保存到你的本地草稿箱！"));
                }));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketRemoteGraphCapabilitiesResponse.TYPE,
                PacketRemoteGraphCapabilitiesResponse.STREAM_CODEC, (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketRemoteGraphListResponse.TYPE,
                PacketRemoteGraphListResponse.STREAM_CODEC, (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketRemoteGraphUploadResponse.TYPE,
                PacketRemoteGraphUploadResponse.STREAM_CODEC, (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketRemoteGraphDownloadResponse.TYPE,
                PacketRemoteGraphDownloadResponse.STREAM_CODEC, (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketRemoteGraphFileOperationResponse.TYPE,
                PacketRemoteGraphFileOperationResponse.STREAM_CODEC, (payload, context) -> context.queue(() -> RemoteGraphClientState.handle(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketOpenDialogue.TYPE,
                PacketOpenDialogue.STREAM_CODEC, (payload, context) -> context.queue(() -> ClientDialogueState.handleOpen(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketCloseDialogue.TYPE,
                PacketCloseDialogue.STREAM_CODEC, (payload, context) -> context.queue(() -> ClientDialogueState.handleClose(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketQuestScreenSnapshot.TYPE,
                PacketQuestScreenSnapshot.STREAM_CODEC, (payload, context) -> context.queue(() -> ClientQuestScreenState.handleSnapshot(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketMarkerSnapshot.TYPE,
                PacketMarkerSnapshot.STREAM_CODEC, (payload, context) -> context.queue(() -> ClientMarkerStore.handleSnapshot(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketMarkerUpsert.TYPE,
                PacketMarkerUpsert.STREAM_CODEC, (payload, context) -> context.queue(() -> ClientMarkerStore.handleUpsert(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketMarkerRemove.TYPE,
                PacketMarkerRemove.STREAM_CODEC, (payload, context) -> context.queue(() -> ClientMarkerStore.handleRemove(payload)));
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, PacketCaptureEntityTemplateResponse.TYPE,
                PacketCaptureEntityTemplateResponse.STREAM_CODEC,
                (payload, context) -> context.queue(() -> EntityTemplatePickerController.handleResponse(payload)));
    }
}
