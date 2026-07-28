package com.mine.geometry_node.core.network.packet.s2c;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mine.geometry_node.core.engine.dialogue.payload.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueSession;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record PacketOpenDialogue(
        UUID sessionId,
        String pageId,
        Component speaker,
        String bodyText,
        String styleId,
        String defaultChoiceId,
        List<Choice> choices,
        Map<String, Object> metadata
) implements CustomPacketPayload {
    private static final Gson GSON = new Gson();
    private static final int MAX_METADATA_JSON_LENGTH = 32767;

    public PacketOpenDialogue {
        pageId = pageId == null ? "" : pageId;
        speaker = speaker == null ? Component.empty() : speaker;
        bodyText = bodyText == null ? "" : bodyText;
        styleId = styleId == null || styleId.isBlank() ? "default" : styleId;
        defaultChoiceId = defaultChoiceId == null ? "" : defaultChoiceId;
        choices = choices == null ? List.of() : choices.stream().filter(choice -> choice != null).toList();
        metadata = metadata == null ? Map.of() : normalizeMap(metadata);
    }

    public static final Type<PacketOpenDialogue> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "open_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenDialogue> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketOpenDialogue::new
    );

    public PacketOpenDialogue(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(32767), ComponentSerialization.STREAM_CODEC.decode(buf), buf.readUtf(32767), buf.readUtf(32767), buf.readUtf(32767), readChoices(buf), readMetadata(buf));
    }

    public static PacketOpenDialogue from(DialogueSession session) {
        var page = session.getCurrentPage();
        if (page == null) {
            return new PacketOpenDialogue(session.getSessionId(), "", Component.empty(), "", "default", "", List.of(), Map.of());
        }

        List<Choice> choices = new ArrayList<>();
        String defaultChoiceId = page.getDefaultChoiceId() == null ? "" : page.getDefaultChoiceId();
        for (DialogueChoicePayload choice : page.getChoices()) {
            choices.add(new Choice(
                    choice.getId(),
                    choice.getText(),
                    choice.isEnabled(),
                    choice.getDisabledReason() == null ? "" : choice.getDisabledReason(),
                    choice.getId().equals(defaultChoiceId),
                    choice.getMetadata()
            ));
        }

        return new PacketOpenDialogue(
                session.getSessionId(),
                page.getId(),
                session.getDialogueContext() == null
                        ? Component.empty()
                        : session.getDialogueContext().resolveSpeakerDisplayName(),
                page.getText(),
                page.getStyleId(),
                defaultChoiceId,
                choices,
                page.getMetadata()
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeUtf(pageId, 32767);
        ComponentSerialization.STREAM_CODEC.encode(buf, speaker);
        buf.writeUtf(bodyText, 32767);
        buf.writeUtf(styleId, 32767);
        buf.writeUtf(defaultChoiceId, 32767);
        buf.writeInt(choices.size());
        for (Choice choice : choices) {
            buf.writeUtf(choice.choiceId(), 32767);
            buf.writeUtf(choice.text(), 32767);
            buf.writeBoolean(choice.enabled());
            buf.writeUtf(choice.disabledReason(), 32767);
            buf.writeBoolean(choice.defaultChoice());
            writeMetadata(buf, choice.metadata());
        }
        writeMetadata(buf, metadata);
    }

    private static List<Choice> readChoices(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Choice> choices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            choices.add(new Choice(buf.readUtf(32767), buf.readUtf(32767), buf.readBoolean(), buf.readUtf(32767), buf.readBoolean(), readMetadata(buf)));
        }
        return choices;
    }

    private static void writeMetadata(RegistryFriendlyByteBuf buf, Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            buf.writeUtf("", MAX_METADATA_JSON_LENGTH);
            return;
        }
        buf.writeUtf(GSON.toJson(normalizeMap(metadata)), MAX_METADATA_JSON_LENGTH);
    }

    private static Map<String, Object> readMetadata(RegistryFriendlyByteBuf buf) {
        String json = buf.readUtf(MAX_METADATA_JSON_LENGTH);
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            JsonElement element = JsonParser.parseString(json);
            Object decoded = GSON.fromJson(element, Object.class);
            if (decoded instanceof Map<?, ?> map) {
                return normalizeMap(map);
            }
        } catch (RuntimeException ignored) {
        }
        return Map.of();
    }

    private static Map<String, Object> normalizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                result.put(String.valueOf(entry.getKey()), normalizeValue(entry.getValue()));
            }
        }
        return result;
    }

    private static List<Object> normalizeList(List<?> raw) {
        List<Object> result = new ArrayList<>();
        for (Object value : raw) {
            if (value != null) {
                result.add(normalizeValue(value));
            }
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return normalizeMap(map);
        }
        if (value instanceof List<?> list) {
            return normalizeList(list);
        }
        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        return String.valueOf(value);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Choice(String choiceId, String text, boolean enabled, String disabledReason, boolean defaultChoice, Map<String, Object> metadata) {
        public Choice {
            choiceId = choiceId == null ? "" : choiceId;
            text = text == null ? "" : text;
            disabledReason = disabledReason == null ? "" : disabledReason;
            metadata = metadata == null ? Map.of() : normalizeMap(metadata);
        }
    }
}
