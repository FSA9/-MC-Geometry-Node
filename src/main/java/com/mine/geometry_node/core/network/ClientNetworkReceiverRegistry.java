package com.mine.geometry_node.core.network;

import com.mine.geometry_node.client.input.ClientBlueprintInputManager;
import com.mine.geometry_node.client.runtime.dialogue.ClientDialogueState;
import com.mine.geometry_node.client.runtime.behavior.ClientBehaviorDebugStore;
import com.mine.geometry_node.client.runtime.marker.ClientMarkerStore;
import com.mine.geometry_node.client.runtime.quest.ClientQuestScreenState;
import com.mine.geometry_node.client.runtime.render.ClientVisualManager;
import com.mine.geometry_node.client.runtime.render.debug.GeometryDebugRenderer;
import com.mine.geometry_node.client.runtime.render.debug.SchematicProjectionRenderer;
import com.mine.geometry_node.client.runtime.render.image.ClientImageAssetManager;
import com.mine.geometry_node.client.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.client.ui.editor.graph.picker.EntityTemplatePickerController;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferService;
import com.mine.geometry_node.client.asset.transfer.ClientAssetTransferPlanState;
import com.mine.geometry_node.client.asset.preview.protocol.ClientAssetPreviewProtocol;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferAccepted;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferQueued;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferDownloadChunk;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferDownloadComplete;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferServerResult;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferUploadAck;
import com.mine.geometry_node.core.network.packet.asset.transfer.PacketAssetTransferPlanResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketCaptureEntityTemplateResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketCloseDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketBehaviorDebugSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerRemove;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketMarkerUpsert;
import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;
import com.mine.geometry_node.core.network.packet.s2c.PacketPlayerInputInterceptions;
import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.asset.repository.PacketRemoteAssetListResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewAccepted;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewChunk;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewComplete;
import com.mine.geometry_node.core.network.packet.asset.preview.PacketAssetPreviewResult;

/** Registers S2C receivers and routes their payloads into client-owned state. */
public final class ClientNetworkReceiverRegistry {
    private static boolean initialized;

    private ClientNetworkReceiverRegistry() {
    }

    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferAccepted.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferService.INSTANCE.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferQueued.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferService.INSTANCE.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferPlanResponse.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferPlanState.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferDownloadChunk.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferService.INSTANCE.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferUploadAck.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferService.INSTANCE.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferDownloadComplete.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferService.INSTANCE.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetTransferServerResult.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetTransferService.INSTANCE.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketSpawnDynamicVisual.TYPE,
                (payload, context) -> context.queue(() -> ClientVisualManager.spawnEffectFromPacket(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketVisualAssetData.TYPE,
                (payload, context) -> context.queue(() -> ClientImageAssetManager.acceptServerAsset(payload.assetId(), payload.data())));
        ClientboundPayloadRegistry.registerClientReceiver(PacketGeometryDebugSnapshot.TYPE,
                (payload, context) -> context.queue(() -> GeometryDebugRenderer.handleSnapshot(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketBehaviorDebugSnapshot.TYPE,
                (payload, context) -> context.queue(() -> ClientBehaviorDebugStore.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketSchematicProjection.TYPE,
                (payload, context) -> context.queue(() -> SchematicProjectionRenderer.handleProjection(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketRemoteAssetCapabilitiesResponse.TYPE,
                (payload, context) -> context.queue(() -> RemoteAssetClient.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketRemoteAssetListResponse.TYPE,
                (payload, context) -> context.queue(() -> RemoteAssetClient.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketRemoteAssetFileOperationResponse.TYPE,
                (payload, context) -> context.queue(() -> RemoteAssetClient.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketOpenDialogue.TYPE,
                (payload, context) -> context.queue(() -> ClientDialogueState.handleOpen(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketPlayerInputInterceptions.TYPE,
                (payload, context) -> context.queue(() -> ClientBlueprintInputManager.setInterceptionMask(payload.mask())));
        ClientboundPayloadRegistry.registerClientReceiver(PacketCloseDialogue.TYPE,
                (payload, context) -> context.queue(() -> ClientDialogueState.handleClose(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketQuestScreenSnapshot.TYPE,
                (payload, context) -> context.queue(() -> ClientQuestScreenState.handleSnapshot(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketMarkerSnapshot.TYPE,
                (payload, context) -> context.queue(() -> ClientMarkerStore.handleSnapshot(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketMarkerUpsert.TYPE,
                (payload, context) -> context.queue(() -> ClientMarkerStore.handleUpsert(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketMarkerRemove.TYPE,
                (payload, context) -> context.queue(() -> ClientMarkerStore.handleRemove(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketCaptureEntityTemplateResponse.TYPE,
                (payload, context) -> context.queue(() -> EntityTemplatePickerController.handleResponse(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetPreviewAccepted.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetPreviewProtocol.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetPreviewChunk.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetPreviewProtocol.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetPreviewComplete.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetPreviewProtocol.handle(payload)));
        ClientboundPayloadRegistry.registerClientReceiver(PacketAssetPreviewResult.TYPE,
                (payload, context) -> context.queue(() -> ClientAssetPreviewProtocol.handle(payload)));
    }
}
