package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.dialogue.payload.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueSession;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PacketOpenDialogue(
        UUID sessionId,
        String pageId,
        String speaker,
        String bodyText,
        String styleId,
        String defaultChoiceId,
        List<Choice> choices
) implements CustomPacketPayload {
    public static final Type<PacketOpenDialogue> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("geometry_node", "open_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenDialogue> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketOpenDialogue::new
    );

    public PacketOpenDialogue(RegistryFriendlyByteBuf buf) {
        this(buf.readUUID(), buf.readUtf(32767), buf.readUtf(32767), buf.readUtf(32767), buf.readUtf(32767), buf.readUtf(32767), readChoices(buf));
    }

    public static PacketOpenDialogue from(DialogueSession session) {
        var page = session.getCurrentPage();
        if (page == null) {
            return new PacketOpenDialogue(session.getSessionId(), "", "", "", "default", "", List.of());
        }

        List<Choice> choices = new ArrayList<>();
        String defaultChoiceId = page.getDefaultChoiceId() == null ? "" : page.getDefaultChoiceId();
        for (DialogueChoicePayload choice : page.getChoices()) {
            choices.add(new Choice(
                    choice.getId(),
                    choice.getText(),
                    choice.isEnabled(),
                    choice.getDisabledReason() == null ? "" : choice.getDisabledReason(),
                    choice.getId().equals(defaultChoiceId)
            ));
        }

        return new PacketOpenDialogue(
                session.getSessionId(),
                page.getId(),
                page.getSpeaker() == null ? "" : page.getSpeaker(),
                page.getText(),
                page.getStyleId(),
                defaultChoiceId,
                choices
        );
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeUtf(pageId, 32767);
        buf.writeUtf(speaker, 32767);
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
        }
    }

    private static List<Choice> readChoices(RegistryFriendlyByteBuf buf) {
        int size = buf.readInt();
        List<Choice> choices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            choices.add(new Choice(buf.readUtf(32767), buf.readUtf(32767), buf.readBoolean(), buf.readUtf(32767), buf.readBoolean()));
        }
        return choices;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Choice(String choiceId, String text, boolean enabled, String disabledReason, boolean defaultChoice) {
    }
}
