package com.mine.geometry_node.core.network.packet.viewport;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.UUID;

/** Accepted means a path was started, not that the entity has arrived. */
public record PacketViewportEntityMoveResult(UUID requestId, int accepted, int requested, BlockPos target,
                                            String reason) implements CustomPacketPayload {
    public PacketViewportEntityMoveResult {
        java.util.Objects.requireNonNull(requestId); java.util.Objects.requireNonNull(target);
        if (requested<1 || requested>PacketViewportEntityMove.MAX_ENTITIES || accepted<0 || accepted>requested
                || !java.util.Set.of("accepted","permission","rate_limit","no_target","unavailable").contains(reason))
            throw new IllegalArgumentException("Invalid viewport movement acknowledgement");
        target=target.immutable();
    }
    public static final Type<PacketViewportEntityMoveResult> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "viewport_entity_move_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketViewportEntityMoveResult> STREAM_CODEC = StreamCodec.of((b,p) -> {
        b.writeUUID(p.requestId); b.writeVarInt(p.accepted); b.writeVarInt(p.requested);
        b.writeBlockPos(p.target); b.writeUtf(p.reason,64);
    }, b -> new PacketViewportEntityMoveResult(b.readUUID(),b.readVarInt(),b.readVarInt(),b.readBlockPos(),b.readUtf(64)));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
