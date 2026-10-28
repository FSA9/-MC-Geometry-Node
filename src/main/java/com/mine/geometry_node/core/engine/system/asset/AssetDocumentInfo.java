package com.mine.geometry_node.core.engine.system.asset;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.metadata.TagPolicy;
import com.mine.geometry_node.core.metadata.TagSet;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Bounded display metadata, separate from the asset's type/variant identity. */
public record AssetDocumentInfo(String description, List<String> tags) {
    public static final int MAX_DESCRIPTION_LENGTH = 2048;
    public static final int MAX_TAGS = TagSet.MAX_TAGS;
    public static final int MAX_TAG_LENGTH = TagSet.MAX_TAG_LENGTH;
    public static final AssetDocumentInfo EMPTY = new AssetDocumentInfo("", List.of());

    public AssetDocumentInfo {
        description = truncate(Objects.requireNonNullElse(description, ""), MAX_DESCRIPTION_LENGTH);
        tags = tags == null ? List.of() : tags.stream().filter(Objects::nonNull)
                .filter(tag -> !tag.isBlank()).map(tag -> truncate(tag, MAX_TAG_LENGTH))
                .distinct().limit(MAX_TAGS).toList();
    }

    /** Non-document formats have no invented description or tags. Called on repository IO threads. */
    public static AssetDocumentInfo inspect(Path file, AssetMetadata identity) {
        if (file == null || identity == null || !"graph".equals(identity.typeId())) return EMPTY;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (!parsed.isJsonObject()) return EMPTY;
            var root = parsed.getAsJsonObject();
            String description = root.has("comment") && root.get("comment").isJsonPrimitive()
                    ? root.get("comment").getAsString() : "";
            List<String> tags = new ArrayList<>();
            if (root.has("tags") && root.get("tags").isJsonArray()) {
                for (JsonElement value : root.getAsJsonArray("tags")) {
                    if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) tags.add(value.getAsString());
                }
            }
            return new AssetDocumentInfo(description, TagSet.normalize(tags, TagPolicy.GRAPH_LEGACY));
        } catch (Exception ignored) {
            return EMPTY;
        }
    }

    private static String truncate(String text, int maximum) {
        if (text.length() <= maximum) return text;
        int end = Character.isHighSurrogate(text.charAt(maximum - 1)) ? maximum - 1 : maximum;
        return text.substring(0, end);
    }
}
