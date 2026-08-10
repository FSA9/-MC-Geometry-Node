package com.mine.geometry_node.core.network.packet.s2c;
import com.mine.geometry_node.core.engine.system.asset.preview.*; import com.mine.geometry_node.core.network.packet.preview.AssetPreviewPacketCodecs; import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.Identifier; import java.util.*;
public record PacketAssetPreviewAccepted(UUID requestId,AssetPreviewDescriptor descriptor) implements CustomPacketPayload {
 public static final Type<PacketAssetPreviewAccepted> TYPE=new Type<>(Identifier.fromNamespaceAndPath("geometry_node","asset_preview_accepted"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PacketAssetPreviewAccepted> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.requestId);AssetPreviewPacketCodecs.writeDescriptor(b,p.descriptor);},b->new PacketAssetPreviewAccepted(b.readUUID(),AssetPreviewPacketCodecs.readDescriptor(b)));
 public PacketAssetPreviewAccepted{requestId=Objects.requireNonNull(requestId);descriptor=Objects.requireNonNull(descriptor);}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
