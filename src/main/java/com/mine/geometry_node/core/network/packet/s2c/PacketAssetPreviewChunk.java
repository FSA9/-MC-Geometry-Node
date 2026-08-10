package com.mine.geometry_node.core.network.packet.s2c;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewLimits; import net.minecraft.network.RegistryFriendlyByteBuf; import net.minecraft.network.codec.StreamCodec; import net.minecraft.network.protocol.common.custom.CustomPacketPayload; import net.minecraft.resources.Identifier; import java.util.*;
public record PacketAssetPreviewChunk(UUID requestId,int sequence,int offset,byte[] content) implements CustomPacketPayload {
 public static final Type<PacketAssetPreviewChunk> TYPE=new Type<>(Identifier.fromNamespaceAndPath("geometry_node","asset_preview_chunk"));
 public static final StreamCodec<RegistryFriendlyByteBuf,PacketAssetPreviewChunk> STREAM_CODEC=StreamCodec.of((b,p)->{b.writeUUID(p.requestId);b.writeVarInt(p.sequence);b.writeVarInt(p.offset);b.writeByteArray(p.content);},b->new PacketAssetPreviewChunk(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readByteArray(AssetPreviewLimits.MAX_CHUNK_BYTES)));
 public PacketAssetPreviewChunk{requestId=Objects.requireNonNull(requestId);content=content==null?new byte[0]:Arrays.copyOf(content,content.length);if(sequence<0||offset<0||content.length==0||content.length>AssetPreviewLimits.MAX_CHUNK_BYTES)throw new IllegalArgumentException("Invalid preview chunk");}
 @Override public byte[] content(){return Arrays.copyOf(content,content.length);}@Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
