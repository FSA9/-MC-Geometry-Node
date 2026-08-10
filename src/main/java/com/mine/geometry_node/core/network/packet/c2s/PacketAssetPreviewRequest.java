package com.mine.geometry_node.core.network.packet.c2s;
import com.mine.geometry_node.core.engine.system.asset.preview.*; import com.mine.geometry_node.core.network.packet.preview.AssetPreviewPacketCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.Identifier;
public record PacketAssetPreviewRequest(AssetPreviewRequest request) implements CustomPacketPayload {
 public static final Type<PacketAssetPreviewRequest> TYPE=new Type<>(Identifier.fromNamespaceAndPath("geometry_node","asset_preview_request"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PacketAssetPreviewRequest> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.request.requestId());AssetPreviewPacketCodecs.writeRevision(b,p.request.revision());},b->new PacketAssetPreviewRequest(new AssetPreviewRequest(b.readUUID(),AssetPreviewPacketCodecs.readRevision(b))));
 public PacketAssetPreviewRequest(java.util.UUID id,AssetPreviewRevision revision){this(new AssetPreviewRequest(id,revision));}
 @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
