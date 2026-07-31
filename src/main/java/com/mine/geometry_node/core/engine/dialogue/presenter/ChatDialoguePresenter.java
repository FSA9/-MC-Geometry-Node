package com.mine.geometry_node.core.engine.dialogue.presenter;

import com.mine.geometry_node.core.engine.dialogue.DialogueSession;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.model.DialogueText;
import com.mine.geometry_node.core.engine.dialogue.richtext.DialogueRichText;
import com.mine.geometry_node.core.engine.dialogue.richtext.DialogueTextParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vanilla chat presenter for the built-in default dialogue style.
 */
public final class ChatDialoguePresenter implements DialoguePresenter {
    public static final String COMMAND_ROOT = "geometry_node";
    public static final String COMMAND_DIALOGUE = "dialogue";
    public static final ChatDialoguePresenter INSTANCE = new ChatDialoguePresenter();

    private ChatDialoguePresenter() {
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public void open(ServerPlayer player, DialogueSession session) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page == null) {
            return;
        }

        MutableComponent body = Component.empty();
        Component speaker = session.getDialogueContext() == null
                ? Component.empty()
                : session.getDialogueContext().resolveDialogueEntityDisplayName();
        if (!speaker.getString().isBlank()) {
            body.append(speaker);
            body.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        }
        body.append(DialogueTextParser.parse(page.bodyText()).component().copy().withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(body);

        boolean hasClosedChoice = false;
        int index = 1;
        for (DialogueChoicePayload choice : page.choices()) {
            if ("closed".equals(choice.id())) {
                hasClosedChoice = true;
                player.sendSystemMessage(closeLine(player, session, choice.text(), choice.enabled(), choice.disabledReason()));
                continue;
            }
            player.sendSystemMessage(choiceLine(player, session, index, choice));
            index++;
        }

        if (!hasClosedChoice) {
            player.sendSystemMessage(closeLine(session));
        }
    }

    private static MutableComponent choiceLine(ServerPlayer player, DialogueSession session, int index, DialogueChoicePayload choice) {
        DialogueRichText text = DialogueTextParser.parse(choice.text());
        MutableComponent line = Component.literal("[" + index + "] ").append(text.component());
        DialogueRichText disabledReason = DialogueTextParser.parse(choice.disabledReason());
        if (!choice.enabled() && !disabledReason.plainText().isBlank()) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(disabledReason.component().copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        boolean clickable = choice.enabled() && isCommandSafeIdentifier(choice.id());
        line.withStyle(style -> choiceStyle(style, clickable, command("choose " + session.getSessionId() + " " + choice.id())));
        return line;
    }

    private static MutableComponent closeLine(DialogueSession session) {
        MutableComponent line = Component.empty().append(Component.translatable("geometry_node.dialogue.close"));
        line.withStyle(style -> choiceStyle(style, true, command("close " + session.getSessionId())));
        return line;
    }

    private static MutableComponent closeLine(ServerPlayer player,
                                              DialogueSession session,
                                              DialogueText text,
                                              boolean enabled,
                                              DialogueText disabledReason) {
        MutableComponent line = Component.empty();
        DialogueRichText parsedText = DialogueTextParser.parse(text);
        if (parsedText.plainText().isBlank() || "Close".equals(parsedText.plainText())) {
            line.append(Component.translatable("geometry_node.dialogue.close"));
        } else {
            line.append(parsedText.component());
        }
        DialogueRichText parsedReason = DialogueTextParser.parse(disabledReason);
        if (!enabled && !parsedReason.plainText().isBlank()) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(parsedReason.component().copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        line.withStyle(style -> choiceStyle(style, enabled, enabled ? command("choose " + session.getSessionId() + " closed") : ""));
        return line;
    }

    private static Style choiceStyle(Style style, boolean clickable, String command) {
        Style result = style.withColor(clickable ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
        if (clickable) {
            result = result.withUnderlined(true)
                    .withClickEvent(new ClickEvent.RunCommand(command));
        }
        return result;
    }

    private static String command(String arguments) {
        return "/" + COMMAND_ROOT + " " + COMMAND_DIALOGUE + " " + arguments;
    }

    private static boolean isCommandSafeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ':' || c == '.')) {
                return false;
            }
        }
        return true;
    }
}
