package com.mine.geometry_node.core.network;

import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferAccepted;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferDownloadChunk;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferDownloadComplete;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferPlanResponse;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferServerResult;
import com.mine.geometry_node.core.network.packet.asset.PacketAssetTransferUploadAck;
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
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteAssetCapabilitiesResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteAssetFileOperationResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketRemoteAssetListResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketSchematicProjection;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncDownload;
import com.mine.geometry_node.core.network.packet.s2c.PacketSyncResponse;
import com.mine.geometry_node.core.network.packet.s2c.PacketVisualAssetData;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewAccepted;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewChunk;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewComplete;
import com.mine.geometry_node.core.network.packet.s2c.PacketAssetPreviewResult;
import dev.architectury.networking.NetworkManager;
import dev.architectury.platform.Platform;
import dev.architectury.utils.Env;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Single source of clientbound payload types and codecs for both physical environments. */
public final class ClientboundPayloadRegistry {
    private static final Map<Identifier, Entry<?>> ENTRIES = new LinkedHashMap<>();

    static {
        add(PacketAssetTransferAccepted.TYPE, PacketAssetTransferAccepted.STREAM_CODEC);
        add(PacketAssetTransferPlanResponse.TYPE, PacketAssetTransferPlanResponse.STREAM_CODEC);
        add(PacketAssetTransferDownloadChunk.TYPE, PacketAssetTransferDownloadChunk.STREAM_CODEC);
        add(PacketAssetTransferUploadAck.TYPE, PacketAssetTransferUploadAck.STREAM_CODEC);
        add(PacketAssetTransferDownloadComplete.TYPE, PacketAssetTransferDownloadComplete.STREAM_CODEC);
        add(PacketAssetTransferServerResult.TYPE, PacketAssetTransferServerResult.STREAM_CODEC);
        add(PacketSpawnDynamicVisual.TYPE, PacketSpawnDynamicVisual.STREAM_CODEC);
        add(PacketVisualAssetData.TYPE, PacketVisualAssetData.STREAM_CODEC);
        add(PacketGeometryDebugSnapshot.TYPE, PacketGeometryDebugSnapshot.STREAM_CODEC);
        add(PacketBehaviorDebugSnapshot.TYPE, PacketBehaviorDebugSnapshot.STREAM_CODEC);
        add(PacketSchematicProjection.TYPE, PacketSchematicProjection.STREAM_CODEC);
        add(PacketSyncResponse.TYPE, PacketSyncResponse.STREAM_CODEC);
        add(PacketSyncDownload.TYPE, PacketSyncDownload.STREAM_CODEC);
        add(PacketRemoteAssetCapabilitiesResponse.TYPE, PacketRemoteAssetCapabilitiesResponse.STREAM_CODEC);
        add(PacketRemoteAssetListResponse.TYPE, PacketRemoteAssetListResponse.STREAM_CODEC);
        add(PacketRemoteAssetFileOperationResponse.TYPE, PacketRemoteAssetFileOperationResponse.STREAM_CODEC);
        add(PacketOpenDialogue.TYPE, PacketOpenDialogue.STREAM_CODEC);
        add(PacketPlayerInputInterceptions.TYPE, PacketPlayerInputInterceptions.STREAM_CODEC);
        add(PacketCloseDialogue.TYPE, PacketCloseDialogue.STREAM_CODEC);
        add(PacketQuestScreenSnapshot.TYPE, PacketQuestScreenSnapshot.STREAM_CODEC);
        add(PacketMarkerSnapshot.TYPE, PacketMarkerSnapshot.STREAM_CODEC);
        add(PacketMarkerUpsert.TYPE, PacketMarkerUpsert.STREAM_CODEC);
        add(PacketMarkerRemove.TYPE, PacketMarkerRemove.STREAM_CODEC);
        add(PacketCaptureEntityTemplateResponse.TYPE, PacketCaptureEntityTemplateResponse.STREAM_CODEC);
        add(PacketAssetPreviewAccepted.TYPE, PacketAssetPreviewAccepted.STREAM_CODEC);
        add(PacketAssetPreviewChunk.TYPE, PacketAssetPreviewChunk.STREAM_CODEC);
        add(PacketAssetPreviewComplete.TYPE, PacketAssetPreviewComplete.STREAM_CODEC);
        add(PacketAssetPreviewResult.TYPE, PacketAssetPreviewResult.STREAM_CODEC);
    }

    private ClientboundPayloadRegistry() {
    }

    /** Dedicated servers need codecs for sending, but must not load client packet handlers. */
    public static void registerDedicatedServerTypes() {
        if (Platform.getEnvironment() != Env.SERVER) return;
        for (Entry<?> entry : ENTRIES.values()) registerTypeUnchecked(entry);
    }

    public static <T extends CustomPacketPayload> void registerClientReceiver(
            CustomPacketPayload.Type<T> type, NetworkManager.NetworkReceiver<T> receiver) {
        Entry<T> entry = entry(type);
        NetworkManager.registerReceiver(NetworkManager.Side.S2C, type, entry.codec(), receiver);
    }

    private static <T extends CustomPacketPayload> void add(
            CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
        Entry<?> previous = ENTRIES.putIfAbsent(type.id(), new Entry<>(type, codec));
        if (previous != null) throw new IllegalStateException("Duplicate clientbound payload type: " + type.id());
    }

    @SuppressWarnings("unchecked")
    private static <T extends CustomPacketPayload> Entry<T> entry(CustomPacketPayload.Type<T> type) {
        Entry<?> entry = ENTRIES.get(type.id());
        if (entry == null) throw new IllegalArgumentException("Unregistered clientbound payload type: " + type.id());
        return (Entry<T>) entry;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerTypeUnchecked(Entry<?> entry) {
        NetworkManager.registerS2CPayloadType((CustomPacketPayload.Type) entry.type(), (StreamCodec) entry.codec());
    }

    private record Entry<T extends CustomPacketPayload>(
            CustomPacketPayload.Type<T> type,
            StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
    }
}
