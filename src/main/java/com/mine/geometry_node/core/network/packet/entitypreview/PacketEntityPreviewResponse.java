package com.mine.geometry_node.core.network.packet.entitypreview;

import com.mine.geometry_node.core.engine.system.entitypreview.*;
import com.mine.geometry_node.core.node.value.entity.EntityTemplateValue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

public record PacketEntityPreviewResponse(long requestId, List<PacketEntityPreviewResponse.Entry> entries) implements CustomPacketPayload {
    public static final Type<PacketEntityPreviewResponse> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "entity_preview_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketEntityPreviewResponse> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                int start = b.writerIndex();
                b.writeLong(p.requestId()); b.writeVarInt(p.entries().size());
                for (Entry entry : p.entries()) {
                    EntityPreviewPacketCodecs.target(b, entry.target());
                    b.writeUtf(entry.message(), EntityPreviewLimits.MAX_MESSAGE_LENGTH);
                    b.writeUtf(entry.template().entityTypeId(), EntityPreviewLimits.MAX_TYPE_LENGTH);
                    if (!entry.template().isEmpty()) b.writeByteArray(EntityPreviewNbt.encode(entry.template().data()));
                }
                if (b.writerIndex() - start > EntityPreviewLimits.MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Entity preview response too large");
            }, b -> {
                if (b.readableBytes() > EntityPreviewLimits.MAX_RESPONSE_BYTES) throw new IllegalArgumentException("Entity preview response too large");
                long id = b.readLong(); int count = EntityPreviewPacketCodecs.count(b);
                List<Entry> entries = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    EntityPreviewTarget target = EntityPreviewPacketCodecs.target(b);
                    String message = b.readUtf(EntityPreviewLimits.MAX_MESSAGE_LENGTH);
                    String type = b.readUtf(EntityPreviewLimits.MAX_TYPE_LENGTH);
                    EntityTemplateValue template = EntityTemplateValue.EMPTY;
                    if (!type.isEmpty()) {
                        if (Identifier.tryParse(type) == null) throw new IllegalArgumentException("Invalid appearance entity type");
                        var data = EntityPreviewNbt.decode(b.readByteArray(EntityPreviewLimits.MAX_APPEARANCE_BYTES));
                        var sanitized = EntityAppearanceSanitizer.sanitize(data);
                        if (!data.equals(sanitized)) throw new IllegalArgumentException("Nonappearance data in entity preview");
                        template = new EntityTemplateValue(type, sanitized);
                    }
                    entries.add(new Entry(target, template, message));
                }
                return new PacketEntityPreviewResponse(id, entries);
            });
    public PacketEntityPreviewResponse {
        entries = List.copyOf(entries);
        if (entries.size() > EntityPreviewLimits.MAX_TARGETS) throw new IllegalArgumentException("Too many entity preview entries");
    }
    public record Entry(EntityPreviewTarget target, EntityTemplateValue template, String message) {
        public Entry {
            Objects.requireNonNull(target); Objects.requireNonNull(template); Objects.requireNonNull(message);
            if (message.length() > EntityPreviewLimits.MAX_MESSAGE_LENGTH || template.entityTypeId().length() > EntityPreviewLimits.MAX_TYPE_LENGTH)
                throw new IllegalArgumentException("Entity preview text limit exceeded");
            if (template.isEmpty() != !message.isEmpty()) throw new IllegalArgumentException("Inconsistent entity preview result");
            if (!template.isEmpty()) {
                var safe = EntityAppearanceSanitizer.sanitize(template.data());
                EntityPreviewNbt.encode(safe);
                template = new EntityTemplateValue(template.entityTypeId(), safe);
            }
        }
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
