package com.mine.geometry_node.core.engine.dialogue.presenter;

import com.mine.geometry_node.core.engine.dialogue.payload.DialogueChoicePayload;
import com.mine.geometry_node.core.engine.dialogue.payload.DialoguePagePayload;
import com.mine.geometry_node.core.engine.dialogue.richtext.DialogueRichText;
import com.mine.geometry_node.core.engine.dialogue.richtext.DialogueTextParser;
import com.mine.geometry_node.core.engine.dialogue.session.DialogueSession;
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
        String speaker = page.getSpeaker();
        if (speaker != null && !speaker.isBlank()) {
            body.append(DialogueTextParser.parse(speaker, player.registryAccess()).component().copy().withStyle(ChatFormatting.GOLD));
            body.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        }
        body.append(DialogueTextParser.parse(page.getText(), player.registryAccess()).component().copy().withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(body);

        boolean hasClosedChoice = false;
        int index = 1;
        for (DialogueChoicePayload choice : page.getChoices()) {
            if ("closed".equals(choice.getId())) {
                hasClosedChoice = true;
                player.sendSystemMessage(closeLine(player, session, choice.getText(), choice.isEnabled(), choice.getDisabledReason()));
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
        DialogueRichText text = DialogueTextParser.parse(choice.getText(), player.registryAccess());
        MutableComponent line = Component.literal("[" + index + "] ").append(text.component());
        if (!choice.isEnabled() && choice.getDisabledReason() != null && !choice.getDisabledReason().isBlank()) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(DialogueTextParser.parse(choice.getDisabledReason(), player.registryAccess()).component().copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        boolean clickable = choice.isEnabled() && isCommandSafeIdentifier(choice.getId());
        line.withStyle(style -> choiceStyle(style, clickable, command("choose " + session.getSessionId() + " " + choice.getId())));
        return line;
    }

    private static MutableComponent closeLine(DialogueSession session) {
        MutableComponent line = Component.empty().append(Component.translatable("geometry_node.dialogue.close"));
        line.withStyle(style -> choiceStyle(style, true, command("close " + session.getSessionId())));
        return line;
    }

    private static MutableComponent closeLine(ServerPlayer player, DialogueSession session, String text, boolean enabled, String disabledReason) {
        MutableComponent line = Component.empty();
        if (text == null || text.isBlank() || "Close".equals(text)) {
            line.append(Component.translatable("geometry_node.dialogue.close"));
        } else {
            line.append(DialogueTextParser.parse(text, player.registryAccess()).component());
        }
        if (!enabled && disabledReason != null && !disabledReason.isBlank()) {
            line.append(Component.literal(" - ").withStyle(ChatFormatting.DARK_GRAY));
            line.append(DialogueTextParser.parse(disabledReason, player.registryAccess()).component().copy().withStyle(ChatFormatting.DARK_GRAY));
        }
        line.withStyle(style -> choiceStyle(style, enabled, enabled ? command("choose " + session.getSessionId() + " closed") : ""));
        return line;
    }

    private static Style choiceStyle(Style style, boolean clickable, String command) {
        Style result = style.withColor(clickable ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
        if (clickable) {
            result = result.withUnderlined(true)
                    .withClickEvent(new ClickEvent.SuggestCommand(command));
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
