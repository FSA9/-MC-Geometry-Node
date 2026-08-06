package com.mine.geometry_node.core.network.packet.s2c;

import com.mine.geometry_node.core.engine.dialogue.DialogueSession;
import com.mine.geometry_node.core.engine.dialogue.DialogueStyleRegistry;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.dialogue.model.shop.ShopPagePayload;
import com.mine.geometry_node.core.network.codec.DialogueTextStreamCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record PacketOpenDialogue(
        UUID sessionId,
        String pageId,
        Component speaker,
        DialoguePagePayload.Content content,
        String defaultChoiceId,
        List<Choice> choices
) implements CustomPacketPayload {
    private static final int MAX_STRING_LENGTH = 32767;
    private static final int MAX_CHOICES = 256;
    private static final int MAX_OFFERS = 4096;
    private static final int MAX_ITEMS_PER_OFFER = 256;

    public PacketOpenDialogue {
        pageId = pageId == null ? "" : pageId;
        speaker = speaker == null ? Component.empty() : speaker.copy();
        content = content == null
                ? new DialoguePagePayload.TextContent(DialogueStyleRegistry.DEFAULT, DialogueText.EMPTY)
                : content;
        defaultChoiceId = defaultChoiceId == null ? "" : defaultChoiceId;
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public static final Type<PacketOpenDialogue> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("geometry_node", "open_dialogue"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PacketOpenDialogue> STREAM_CODEC = StreamCodec.of(
            (buf, packet) -> packet.write(buf),
            PacketOpenDialogue::new
    );

    public PacketOpenDialogue(RegistryFriendlyByteBuf buf) {
        this(
                buf.readUUID(),
                buf.readUtf(MAX_STRING_LENGTH),
                ComponentSerialization.STREAM_CODEC.decode(buf),
                readContent(buf),
                buf.readUtf(MAX_STRING_LENGTH),
                readChoices(buf)
        );
    }

    public static PacketOpenDialogue from(DialogueSession session) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page == null) {
            return new PacketOpenDialogue(
                    session.getSessionId(),
                    "",
                    Component.empty(),
                    new DialoguePagePayload.TextContent(DialogueStyleRegistry.DEFAULT, DialogueText.EMPTY),
                    "",
                    List.of()
            );
        }

        return fromPage(
                session.getSessionId(),
                session.getDialogueContext() == null
                        ? Component.empty()
                        : session.getDialogueContext().resolveDialogueEntityDisplayName(),
                page
        );
    }

    public static PacketOpenDialogue fromPage(UUID sessionId,
                                              Component speaker,
                                              DialoguePagePayload page) {
        if (page == null) {
            return new PacketOpenDialogue(
                    sessionId,
                    "",
                    speaker,
                    new DialoguePagePayload.TextContent(DialogueStyleRegistry.DEFAULT, DialogueText.EMPTY),
                    "",
                    List.of()
            );
        }
        List<Choice> choices = new ArrayList<>(page.choices().size());
        for (DialogueChoicePayload choice : page.choices()) {
            choices.add(new Choice(
                    choice.id(),
                    choice.text(),
                    choice.enabled(),
                    choice.disabledReason(),
                    choice.id().equals(page.defaultChoiceId())
            ));
        }

        return new PacketOpenDialogue(
                sessionId,
                page.id(),
                speaker,
                page.content(),
                page.defaultChoiceId(),
                choices
        );
    }

    public String styleId() {
        return content.styleId();
    }

    @Override
    public Component speaker() {
        return speaker.copy();
    }

    public DialogueText bodyText() {
        return content instanceof DialoguePagePayload.TextContent textContent
                ? textContent.text()
                : DialogueText.EMPTY;
    }

    @Nullable
    public ShopPagePayload shop() {
        return content instanceof DialoguePagePayload.ShopContent shopContent ? shopContent.shop() : null;
    }

    public void write(RegistryFriendlyByteBuf buf) {
        buf.writeUUID(sessionId);
        buf.writeUtf(pageId, MAX_STRING_LENGTH);
        ComponentSerialization.STREAM_CODEC.encode(buf, speaker);
        writeContent(buf, content);
        buf.writeUtf(defaultChoiceId, MAX_STRING_LENGTH);
        writeCount(buf, choices.size(), MAX_CHOICES, "dialogue choices");
        for (Choice choice : choices) {
            buf.writeUtf(choice.choiceId(), MAX_STRING_LENGTH);
            DialogueTextStreamCodec.STREAM_CODEC.encode(buf, choice.text());
            buf.writeBoolean(choice.enabled());
            DialogueTextStreamCodec.STREAM_CODEC.encode(buf, choice.disabledReason());
            buf.writeBoolean(choice.defaultChoice());
        }
    }

    private static void writeContent(RegistryFriendlyByteBuf buf, DialoguePagePayload.Content content) {
        switch (content) {
            case DialoguePagePayload.TextContent text -> {
                buf.writeByte(0);
                buf.writeUtf(text.styleId(), MAX_STRING_LENGTH);
                DialogueTextStreamCodec.STREAM_CODEC.encode(buf, text.text());
            }
            case DialoguePagePayload.ShopContent shop -> {
                buf.writeByte(1);
                writeShop(buf, shop.shop());
            }
        }
    }

    private static DialoguePagePayload.Content readContent(RegistryFriendlyByteBuf buf) {
        return switch (buf.readUnsignedByte()) {
            case 0 -> new DialoguePagePayload.TextContent(
                    buf.readUtf(MAX_STRING_LENGTH),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf)
            );
            case 1 -> new DialoguePagePayload.ShopContent(readShop(buf));
            default -> throw new IllegalArgumentException("Unknown dialogue page content type");
        };
    }

    private static List<Choice> readChoices(RegistryFriendlyByteBuf buf) {
        int size = readCount(buf, MAX_CHOICES, "dialogue choices");
        List<Choice> choices = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            choices.add(new Choice(
                    buf.readUtf(MAX_STRING_LENGTH),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf),
                    buf.readBoolean(),
                    DialogueTextStreamCodec.STREAM_CODEC.decode(buf),
                    buf.readBoolean()
            ));
        }
        return List.copyOf(choices);
    }

    private static void writeShop(RegistryFriendlyByteBuf buf, ShopPagePayload shop) {
        buf.writeUtf(shop.shopId(), MAX_STRING_LENGTH);
        buf.writeUtf(shop.title(), MAX_STRING_LENGTH);
        buf.writeUtf(shop.feedback().message(), MAX_STRING_LENGTH);
        buf.writeUtf(shop.feedback().messageKey(), MAX_STRING_LENGTH);
        buf.writeBoolean(shop.feedback().success());
        writeCount(buf, shop.offers().size(), MAX_OFFERS, "shop offers");
        for (ShopPagePayload.Offer offer : shop.offers()) {
            buf.writeUtf(offer.id(), MAX_STRING_LENGTH);
            buf.writeUtf(offer.title(), MAX_STRING_LENGTH);
            buf.writeVarInt(offer.maxUses());
            buf.writeVarInt(offer.uses());
            buf.writeBoolean(offer.enabled());
            buf.writeUtf(offer.disabledReason(), MAX_STRING_LENGTH);
            buf.writeBoolean(offer.consumeSellerItems());
            buf.writeBoolean(offer.sellerReceivesPayment());
            writeItems(buf, offer.costs());
            writeItems(buf, offer.rewards());
        }
    }

    private static ShopPagePayload readShop(RegistryFriendlyByteBuf buf) {
        String shopId = buf.readUtf(MAX_STRING_LENGTH);
        String title = buf.readUtf(MAX_STRING_LENGTH);
        ShopPagePayload.Feedback feedback = new ShopPagePayload.Feedback(
                buf.readUtf(MAX_STRING_LENGTH),
                buf.readUtf(MAX_STRING_LENGTH),
                buf.readBoolean()
        );
        int offerCount = readCount(buf, MAX_OFFERS, "shop offers");
        List<ShopPagePayload.Offer> offers = new ArrayList<>(offerCount);
        for (int i = 0; i < offerCount; i++) {
            offers.add(new ShopPagePayload.Offer(
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readBoolean(),
                    buf.readUtf(MAX_STRING_LENGTH),
                    buf.readBoolean(),
                    buf.readBoolean(),
                    readItems(buf),
                    readItems(buf)
            ));
        }
        return new ShopPagePayload(shopId, title, feedback, offers);
    }

    private static void writeItems(RegistryFriendlyByteBuf buf, List<ShopPagePayload.Item> items) {
        writeCount(buf, items.size(), MAX_ITEMS_PER_OFFER, "shop items");
        for (ShopPagePayload.Item item : items) {
            buf.writeUtf(item.stackJson(), MAX_STRING_LENGTH);
        }
    }

    private static List<ShopPagePayload.Item> readItems(RegistryFriendlyByteBuf buf) {
        int count = readCount(buf, MAX_ITEMS_PER_OFFER, "shop items");
        List<ShopPagePayload.Item> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(new ShopPagePayload.Item(buf.readUtf(MAX_STRING_LENGTH)));
        }
        return List.copyOf(result);
    }

    private static void writeCount(RegistryFriendlyByteBuf buf, int count, int maximum, String label) {
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Too many " + label + ": " + count);
        }
        buf.writeVarInt(count);
    }

    private static int readCount(RegistryFriendlyByteBuf buf, int maximum, String label) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + label + " count: " + count);
        }
        return count;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public record Choice(
            String choiceId,
            DialogueText text,
            boolean enabled,
            DialogueText disabledReason,
            boolean defaultChoice
    ) {
        public Choice {
            choiceId = choiceId == null ? "" : choiceId;
            text = text == null ? DialogueText.EMPTY : text;
            disabledReason = disabledReason == null ? DialogueText.EMPTY : disabledReason;
        }
    }
}
