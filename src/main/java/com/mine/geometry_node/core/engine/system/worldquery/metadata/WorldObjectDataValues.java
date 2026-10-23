package com.mine.geometry_node.core.engine.system.worldquery.metadata;

import com.mine.geometry_node.core.engine.metadata.GraphMetadataNbt;
import com.mine.geometry_node.core.engine.metadata.GraphMetadataValuePolicy;
import com.mine.geometry_node.core.metadata.DataDictionary;
import com.mine.geometry_node.core.metadata.DataValuePolicy;
import io.netty.buffer.Unpooled;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;

/** Bounded, lossless custom metadata transport. This never reads vanilla entity NBT. */
public final class WorldObjectDataValues {
    public static final int MAX_READ_BYTES = 256 * 1024;
    public static final int MAX_WRITE_BYTES = 28 * 1024;
    private static final DataValuePolicy BOUNDED_STRUCTURE = new DataValuePolicy() {
        public Object snapshot(Object value) { return GraphMetadataValuePolicy.runtime().snapshot(value); }
        public String validateKey(String key) { return Objects.requireNonNull(key); }
    };
    private WorldObjectDataValues() {}
    public static Map<String, Object> snapshot(Map<String, ?> values) {
        // Historical scoped dictionaries retain their key rules; transport still bounds graph complexity.
        DataDictionary.validateStructure(values, BOUNDED_STRUCTURE);
        return DataDictionary.snapshot(values, GraphMetadataValuePolicy.runtime());
    }
    public static boolean equivalent(Map<String, ?> first, Map<String, ?> second) {
        return DataDictionary.equivalent(first, second, GraphMetadataValuePolicy.runtime());
    }
    public static byte[] encode(Map<String, ?> values, HolderLookup.Provider registries, int maximum) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer(Math.min(256, maximum), maximum));
        try {
            buffer.writeNbt(GraphMetadataNbt.encode(snapshot(values), registries));
            byte[] bytes = new byte[buffer.readableBytes()]; buffer.readBytes(bytes); return bytes;
        } catch (IndexOutOfBoundsException limit) {
            throw new IllegalArgumentException("Custom data payload exceeds byte limit", limit);
        } finally { buffer.release(); }
    }
    public static Map<String, Object> decode(byte[] bytes, HolderLookup.Provider registries) {
        if (bytes.length > MAX_READ_BYTES) throw new IllegalArgumentException("Custom data payload too large");
        var buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        try {
            Tag tag = buffer.readNbt(new NbtAccounter(4L * MAX_READ_BYTES, 96));
            if (buffer.isReadable()) throw new IllegalArgumentException("Trailing custom data bytes");
            Object value = GraphMetadataNbt.decode(tag, registries);
            if (!(value instanceof Map<?, ?> map)) throw new IllegalArgumentException("Custom data must be a dictionary");
            @SuppressWarnings("unchecked") var typed = (Map<String, Object>) map;
            return snapshot(typed);
        } finally { buffer.release(); }
    }
    /** Hash a canonical representation: map insertion order must not create false edit conflicts. */
    public static String revision(Map<String, ?> values, HolderLookup.Provider registries) {
        Tag tag = GraphMetadataNbt.encode(snapshot(values), registries);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256"); hash(tag, digest);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void hash(Tag tag, MessageDigest digest) {
        digest.update(tag.getId());
        if (tag instanceof CompoundTag compound) {
            for (String key : new TreeSet<>(compound.keySet())) {
                hashText(key, digest); hash(compound.get(key), digest);
            }
            digest.update((byte) 0);
        } else if (tag instanceof ListTag list) {
            hashText(Integer.toString(list.size()), digest);
            for (Tag value : list) hash(value, digest);
        } else hashText(tag.toString(), digest);
    }
    private static void hashText(String text, MessageDigest digest) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update((byte)(bytes.length >>> 24)); digest.update((byte)(bytes.length >>> 16));
        digest.update((byte)(bytes.length >>> 8)); digest.update((byte)bytes.length); digest.update(bytes);
    }
    public static String summary(Map<String, ?> values) {
        StringJoiner text = new StringJoiner(" · "); int count = 0;
        for (var item : values.entrySet()) {
            if (count++ == 2) break;
            Object value = item.getValue();
            String brief = value instanceof Map<?, ?> map ? "{" + map.size() + "}"
                    : value instanceof Collection<?> list ? "[" + list.size() + "]"
                    : value instanceof String || value instanceof Number || value instanceof Boolean || value instanceof UUID
                    ? clip(String.valueOf(value), 48) : "…";
            text.add(clip(item.getKey(), 32) + "=" + brief);
        }
        return clip(text.toString(), 192);
    }
    private static String clip(String text, int max) {
        text = text.replace('\n', ' ').replace('\r', ' ').replace('\t', ' ');
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
