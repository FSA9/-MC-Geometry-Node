package com.mine.geometry_node.core.network.packet.entitypreview;

import com.mine.geometry_node.core.engine.system.entitypreview.protocol.EntityPreviewLimits;
import com.mine.geometry_node.core.engine.system.entitypreview.protocol.EntityPreviewTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public record PacketEntityPreviewRequest(long requestId, List<EntityPreviewTarget> targets) implements CustomPacketPayload {
    public static final Type<PacketEntityPreviewRequest> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_preview_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityPreviewRequest> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                int start = b.writerIndex();
                b.writeLong(p.requestId()); b.writeVarInt(p.targets().size());
                for (EntityPreviewTarget target : p.targets()) EntityPreviewPacketCodecs.target(b, target);
                if (b.writerIndex() - start > EntityPreviewLimits.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Entity preview request too large");
            }, b -> {
                if (b.readableBytes() > EntityPreviewLimits.MAX_REQUEST_BYTES) throw new IllegalArgumentException("Entity preview request too large");
                long id = b.readLong(); int count = EntityPreviewPacketCodecs.count(b);
                List<EntityPreviewTarget> targets = new ArrayList<>(count);
                for (int i = 0; i < count; i++) targets.add(EntityPreviewPacketCodecs.target(b));
                return new PacketEntityPreviewRequest(id, targets);
            });
    public PacketEntityPreviewRequest {
        targets = List.copyOf(targets);
        if (targets.size() > EntityPreviewLimits.MAX_TARGETS) throw new IllegalArgumentException("Too many entity preview targets");
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
