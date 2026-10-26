package com.mine.geometry_node.core.network.packet.entitypreview;

import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewLimits;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewTarget;
import net.minecraft.network.RegistryFriendlyByteBuf;

final class EntityPreviewPacketCodecs {
    private EntityPreviewPacketCodecs() {}
    static int count(RegistryFriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > EntityPreviewLimits.MAX_TARGETS) throw new IllegalArgumentException("Invalid entity preview batch size");
        return count;
    }
    static void target(RegistryFriendlyByteBuf b, EntityPreviewTarget target) {
        b.writeUUID(target.entityId()); b.writeUtf(target.dimension(), EntityPreviewTarget.MAX_DIMENSION_LENGTH);
    }
    static EntityPreviewTarget target(RegistryFriendlyByteBuf b) {
        return new EntityPreviewTarget(b.readUUID(), b.readUtf(EntityPreviewTarget.MAX_DIMENSION_LENGTH));
    }
}
