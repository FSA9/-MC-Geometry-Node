package com.mine.geometry_node.core.network.packet.asset.preview;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.Identifier; import java.util.*;
public record PacketAssetPreviewComplete(UUID requestId) implements CustomPacketPayload {
 public static final Type<PacketAssetPreviewComplete> TYPE=new Type<>(Identifier.fromNamespaceAndPath("geometry_node","asset_preview_complete")); public static final StreamCodec<RegistryFriendlyByteBuf,PacketAssetPreviewComplete> STREAM_CODEC=StreamCodec.of((b,p)->b.writeUUID(p.requestId),b->new PacketAssetPreviewComplete(b.readUUID()));
 public PacketAssetPreviewComplete{requestId=Objects.requireNonNull(requestId);}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
