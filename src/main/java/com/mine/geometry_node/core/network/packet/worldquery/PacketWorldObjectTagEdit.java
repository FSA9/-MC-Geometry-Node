package com.mine.geometry_node.core.network.packet.worldquery;

import com.mine.geometry_node.core.engine.system.worldquery.WorldObjectEntry;
import com.mine.geometry_node.core.metadata.TagSet;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import java.util.*;

/** One bounded add/remove with an opaque connection-owned capability lease and expected custom tags. */
public record PacketWorldObjectTagEdit(long requestId, String target, String key, List<String> expectedTags,
                                       String tag, boolean add) implements CustomPacketPayload {
    public static final int MAX_EXPECTED_BYTES = 128 * 1024;
    public static final Type<PacketWorldObjectTagEdit> TYPE = new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "world_object_tag_edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PacketWorldObjectTagEdit> STREAM_CODEC = StreamCodec.of(
            (b, p) -> {
                b.writeLong(p.requestId()); b.writeUtf(p.target(), 36); b.writeUtf(p.key(), WorldObjectEntry.MAX_TEXT);
                WorldObjectPacketCodecs.strings(b, p.expectedTags(), TagSet.MAX_TAG_LENGTH);
                b.writeUtf(p.tag(), TagSet.MAX_TAG_LENGTH); b.writeBoolean(p.add());
            }, b -> new PacketWorldObjectTagEdit(b.readLong(), b.readUtf(36), b.readUtf(WorldObjectEntry.MAX_TEXT),
                    expected(b), b.readUtf(TagSet.MAX_TAG_LENGTH), b.readBoolean()));
    public PacketWorldObjectTagEdit {
        Objects.requireNonNull(target); Objects.requireNonNull(key); Objects.requireNonNull(tag);
        expectedTags = List.copyOf(expectedTags);
        if (target.length() > 36 || key.length() > WorldObjectEntry.MAX_TEXT || tag.length() > TagSet.MAX_TAG_LENGTH
                || expectedTags.size() > TagSet.MAX_TAGS) throw new IllegalArgumentException("Tag edit limits exceeded");
        int bytes = 0;
        for (String value : expectedTags) {
            if (value.length() > TagSet.MAX_TAG_LENGTH) throw new IllegalArgumentException("Expected tag too long");
            bytes += 5 + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        }
        if (bytes > MAX_EXPECTED_BYTES) throw new IllegalArgumentException("Expected tags too large");
    }
    private static List<String> expected(RegistryFriendlyByteBuf b) {
        int count = WorldObjectPacketCodecs.count(b, TagSet.MAX_TAGS), bytes = 0;
        List<String> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String value = b.readUtf(TagSet.MAX_TAG_LENGTH);
            bytes += 5 + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
            if (bytes > MAX_EXPECTED_BYTES) throw new IllegalArgumentException("Expected tags too large");
            result.add(value);
        }
        return result;
    }
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
