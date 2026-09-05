package com.mine.geometry_node.core.network.packet.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/** Complete preview result. Preview artifacts are small enough to use one logical NeoForge payload. */
public record PacketAssetPreviewResponse(
        UUID requestId,
        AssetPreviewResultCode code,
        String detail,
        @Nullable AssetPreviewDescriptor descriptor,
        byte[] content
) implements CustomPacketPayload {
    public static final Type<PacketAssetPreviewResponse> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "asset_preview_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketAssetPreviewResponse> STREAM_CODEC = StreamCodec.of(
            PacketAssetPreviewResponse::write, PacketAssetPreviewResponse::read);

    public PacketAssetPreviewResponse {
        requestId = Objects.requireNonNull(requestId, "requestId");
        code = Objects.requireNonNull(code, "code");
        detail = Objects.requireNonNullElse(detail, "");
        content = Objects.requireNonNull(content, "content");
        if (detail.length() > AssetPreviewLimits.PACKET_DETAIL_MAX_LENGTH) {
            throw new IllegalArgumentException("Asset preview detail is too large");
        }
        if (content.length > AssetPreviewLimits.MAX_ENCODED_BYTES) {
            throw new IllegalArgumentException("Asset preview content is too large");
        }
        if (code == AssetPreviewResultCode.AVAILABLE) {
            if (descriptor == null || content.length != descriptor.encodedBytes()) {
                throw new IllegalArgumentException("Available preview requires matching descriptor and content");
            }
        } else if (descriptor != null || content.length != 0) {
            throw new IllegalArgumentException("Unavailable preview cannot carry content");
        }
    }

    public static PacketAssetPreviewResponse available(
            UUID requestId, AssetPreviewDescriptor descriptor, byte[] content) {
        return new PacketAssetPreviewResponse(
                requestId, AssetPreviewResultCode.AVAILABLE, "", descriptor, content);
    }

    public static PacketAssetPreviewResponse failure(
            UUID requestId, AssetPreviewResultCode code, String detail) {
        if (code == AssetPreviewResultCode.AVAILABLE) {
            throw new IllegalArgumentException("Available preview is not a failure");
        }
        return new PacketAssetPreviewResponse(requestId, code, detail, null, new byte[0]);
    }

    private static void write(RegistryFriendlyByteBuf buffer, PacketAssetPreviewResponse packet) {
        buffer.writeUUID(packet.requestId);
        buffer.writeVarInt(packet.code.ordinal());
        buffer.writeUtf(packet.detail, AssetPreviewLimits.PACKET_DETAIL_MAX_LENGTH);
        buffer.writeBoolean(packet.descriptor != null);
        if (packet.descriptor != null) {
            AssetPreviewPacketCodecs.writeDescriptor(buffer, packet.descriptor);
        }
        buffer.writeByteArray(packet.content);
    }

    private static PacketAssetPreviewResponse read(RegistryFriendlyByteBuf buffer) {
        UUID requestId = buffer.readUUID();
        AssetPreviewResultCode code = AssetPreviewPacketCodecs.readEnum(buffer, AssetPreviewResultCode.values());
        String detail = buffer.readUtf(AssetPreviewLimits.PACKET_DETAIL_MAX_LENGTH);
        AssetPreviewDescriptor descriptor = buffer.readBoolean()
                ? AssetPreviewPacketCodecs.readDescriptor(buffer)
                : null;
        byte[] content = buffer.readByteArray(AssetPreviewLimits.MAX_ENCODED_BYTES);
        return new PacketAssetPreviewResponse(requestId, code, detail, descriptor, content);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
