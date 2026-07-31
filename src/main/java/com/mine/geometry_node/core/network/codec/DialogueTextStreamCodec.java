package com.mine.geometry_node.core.network.codec;

import com.google.gson.Gson;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueText;
import com.mine.geometry_node.core.node.value.RichTextValue;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;

import java.util.Map;

/**
 * Tagged wire codec for dialogue text.
 */
public final class DialogueTextStreamCodec {
    private static final int MAX_TEXT_LENGTH = 32767;
    private static final Gson GSON = new Gson();

    public static final StreamCodec<RegistryFriendlyByteBuf, DialogueText> STREAM_CODEC = StreamCodec.of(
            DialogueTextStreamCodec::encode,
            DialogueTextStreamCodec::decode
    );

    private DialogueTextStreamCodec() {
    }

    private static void encode(RegistryFriendlyByteBuf buf, DialogueText text) {
        switch (text == null ? DialogueText.EMPTY : text) {
            case DialogueText.Plain plain -> {
                buf.writeByte(0);
                buf.writeUtf(plain.value(), MAX_TEXT_LENGTH);
            }
            case DialogueText.ComponentText component -> {
                buf.writeByte(1);
                ComponentSerialization.STREAM_CODEC.encode(buf, component.value());
            }
            case DialogueText.Rich rich -> {
                buf.writeByte(2);
                buf.writeUtf(rich.value().toJsonString(), MAX_TEXT_LENGTH);
            }
        }
    }

    private static DialogueText decode(RegistryFriendlyByteBuf buf) {
        return switch (buf.readUnsignedByte()) {
            case 0 -> DialogueText.plain(buf.readUtf(MAX_TEXT_LENGTH));
            case 1 -> DialogueText.component(ComponentSerialization.STREAM_CODEC.decode(buf));
            case 2 -> DialogueText.rich(decodeRichText(buf.readUtf(MAX_TEXT_LENGTH)));
            default -> throw new IllegalArgumentException("Unknown dialogue text wire type");
        };
    }

    private static RichTextValue decodeRichText(String json) {
        Object decoded = GSON.fromJson(json, Object.class);
        if (!(decoded instanceof Map<?, ?> map)
                || !RichTextValue.TYPE.equals(String.valueOf(map.get("type")))) {
            throw new IllegalArgumentException("Invalid rich dialogue text payload");
        }
        return RichTextValue.from(map);
    }
}
