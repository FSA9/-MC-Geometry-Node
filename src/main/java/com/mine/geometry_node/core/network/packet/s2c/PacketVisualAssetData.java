package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.visual.image.ImageAssetValidator;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record PacketVisualAssetData(String assetId, byte[] data) implements CustomPacketPayload {
    public static final Type<PacketVisualAssetData> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath("geometry_node", "visual_asset_data")
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketVisualAssetData> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketVisualAssetData::new
    );

    public PacketVisualAssetData(RegistryFriendlyByteBuf buf) {
        this(buf.readUtf(128), buf.readByteArray(ImageAssetValidator.MAX_ENCODED_BYTES));
    }

    private void write(RegistryFriendlyByteBuf buf) {
        buf.writeUtf(assetId, 128);
        buf.writeByteArray(data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
