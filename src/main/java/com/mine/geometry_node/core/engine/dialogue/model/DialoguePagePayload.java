package com.mine.geometry_node.core.engine.dialogue.model;

import com.mine.geometry_node.core.engine.dialogue.DialogueStyleRegistry;
import com.mine.geometry_node.core.engine.dialogue.model.shop.ShopPagePayload;

import java.util.List;

/**
 * Immutable server-side snapshot of one dialogue page.
 */
public record DialoguePagePayload(
        String id,
        Content content,
        List<DialogueChoicePayload> choices
) {
    public DialoguePagePayload {
        id = id == null ? "" : id;
        if (content == null) {
            throw new IllegalArgumentException("Dialogue page content must not be null");
        }
        choices = choices == null ? List.of() : List.copyOf(choices);
    }

    public static DialoguePagePayload text(String id,
                                           DialogueText text,
                                           String styleId,
                                           List<DialogueChoicePayload> choices) {
        return new DialoguePagePayload(id, new TextContent(styleId, text), choices);
    }

    public static DialoguePagePayload shop(String id,
                                           ShopPagePayload shop,
                                           List<DialogueChoicePayload> choices) {
        return new DialoguePagePayload(id, new ShopContent(shop), choices);
    }

    public String styleId() {
        return content.styleId();
    }

    public DialogueText bodyText() {
        return content instanceof TextContent textContent ? textContent.text() : DialogueText.EMPTY;
    }

    public String defaultChoiceId() {
        for (DialogueChoicePayload choice : choices) {
            if (choice.enabled()) {
                return choice.id();
            }
        }
        return "";
    }

    public DialoguePagePayload withShop(ShopPagePayload shop) {
        if (!(content instanceof ShopContent)) {
            throw new IllegalStateException("Cannot attach shop data to a text page");
        }
        return DialoguePagePayload.shop(id, shop, choices);
    }

    public sealed interface Content permits TextContent, ShopContent {
        String styleId();
    }

    public record TextContent(String styleId, DialogueText text) implements Content {
        public TextContent {
            styleId = styleId == null || styleId.isBlank() ? DialogueStyleRegistry.DEFAULT : styleId;
            text = text == null ? DialogueText.EMPTY : text;
        }
    }

    public record ShopContent(ShopPagePayload shop) implements Content {
        public ShopContent {
            if (shop == null) {
                throw new IllegalArgumentException("Shop content must not be null");
            }
        }

        @Override
        public String styleId() {
            return DialogueStyleRegistry.SHOP;
        }
    }
}
