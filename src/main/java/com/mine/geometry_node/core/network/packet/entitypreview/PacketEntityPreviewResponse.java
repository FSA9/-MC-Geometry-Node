package com.mine.geometry_node.core.network.packet.entitypreview;

import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceLimits;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSanitizer;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceSpec;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewPayload;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewLimits;
import com.mine.geometry_node.core.engine.system.entity.appearance.EntityAppearanceNbt;
import com.mine.geometry_node.core.engine.system.entity.preview.protocol.EntityPreviewTarget;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public record PacketEntityPreviewResponse(long requestId, List<PacketEntityPreviewResponse.Entry> entries) implements CustomPacketPayload {
    public static final Type<PacketEntityPreviewResponse> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_preview_response_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityPreviewResponse> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                int start = b.writerIndex();
                b.writeLong(p.requestId()); b.writeVarInt(p.entries().size());
                for (Entry entry : p.entries()) {
                    EntityPreviewPacketCodecs.target(b, entry.target());
                    b.writeUtf(entry.message(), EntityPreviewLimits.MAX_MESSAGE_LENGTH);
                    b.writeUtf(entry.template().entityTypeId(), EntityAppearanceLimits.MAX_TYPE_LENGTH);
                    if (!entry.template().isEmpty()) b.writeByteArray(EntityAppearanceNbt.encode(entry.template().data()));
                    EntityAppearanceSpec.STREAM_CODEC.encode(b, entry.appearanceSpec());
                }
                if (b.writerIndex() - start > EntityPreviewLimits.MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Entity preview response too large");
            }, b -> {
                if (b.readableBytes() > EntityPreviewLimits.MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Entity preview response too large");
                long id = b.readLong(); int count = EntityPreviewPacketCodecs.count(b);
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    EntityPreviewTarget target = EntityPreviewPacketCodecs.target(b);
                    String message = b.readUtf(EntityPreviewLimits.MAX_MESSAGE_LENGTH);
                    String type = b.readUtf(EntityAppearanceLimits.MAX_TYPE_LENGTH);
                    EntityTemplateValue template = EntityTemplateValue.EMPTY;
                    if (!type.isEmpty()) {
                        if (Identifier.tryParse(type) == null) throw new IllegalArgumentException("Invalid appearance entity type");
                        var data = EntityAppearanceNbt.decode(b.readByteArray(EntityAppearanceLimits.MAX_APPEARANCE_BYTES));
                        var sanitized = EntityAppearanceSanitizer.sanitize(data);
                        if (!data.equals(sanitized)) throw new IllegalArgumentException("Nonappearance data in entity preview");
                        template = new EntityTemplateValue(type, sanitized);
                    }
                    EntityAppearanceSpec spec = EntityAppearanceSpec.STREAM_CODEC.decode(b);
                    entries.add(new Entry(target, new EntityPreviewPayload(template, spec), message));
                }
                return new PacketEntityPreviewResponse(id, entries);
            });
    public PacketEntityPreviewResponse {
        entries = List.copyOf(entries);
        if (entries.size() > EntityPreviewLimits.MAX_TARGETS) throw new IllegalArgumentException("Too many entity preview entries");
    }
    public record Entry(EntityPreviewTarget target, EntityPreviewPayload payload, String message) {
        public Entry {
            Objects.requireNonNull(target); Objects.requireNonNull(payload); Objects.requireNonNull(message);
            if (message.length() > EntityPreviewLimits.MAX_MESSAGE_LENGTH)
                throw new IllegalArgumentException("Entity preview text limit exceeded");
            if (payload.isEmpty() != !message.isEmpty()) throw new IllegalArgumentException("Inconsistent entity preview result");
        }
        public Entry(EntityPreviewTarget target, EntityTemplateValue template, String message) {
            this(target, new EntityPreviewPayload(template, EntityAppearanceSpec.NONE), message);
        }
        public EntityTemplateValue template() { return payload.template(); }
        public EntityAppearanceSpec appearanceSpec() { return payload.appearanceSpec(); }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
