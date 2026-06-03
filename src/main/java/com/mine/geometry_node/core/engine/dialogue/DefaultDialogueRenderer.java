package com.mine.geometry_node.core.engine.dialogue;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.level.ServerPlayer;

/**
 * Vanilla chat renderer for the built-in default dialogue style.
 */
public final class DefaultDialogueRenderer {
    public static final String COMMAND_ROOT = "geometry_node";
    public static final String COMMAND_DIALOGUE = "dialogue";

    private DefaultDialogueRenderer() {
    }

    public static void render(ServerPlayer player, DialogueSession session) {
        DialoguePagePayload page = session.getCurrentPage();
        if (page == null) {
            return;
        }

        MutableComponent body = Component.empty();
        String speaker = page.getSpeaker();
        if (speaker != null && !speaker.isBlank()) {
            body.append(Component.literal(speaker).withStyle(ChatFormatting.GOLD));
            body.append(Component.literal(": ").withStyle(ChatFormatting.GRAY));
        }
        body.append(Component.literal(page.getText()).withStyle(ChatFormatting.WHITE));
        player.sendSystemMessage(body);

        boolean hasClosedChoice = false;
        int index = 1;
        for (DialogueChoicePayload choice : page.getChoices()) {
            if ("closed".equals(choice.getId())) {
                hasClosedChoice = true;
                player.sendSystemMessage(closeLine(session, choice.getText(), choice.isEnabled()));
                continue;
            }
            player.sendSystemMessage(choiceLine(session, index, choice));
            index++;
        }

        if (!hasClosedChoice) {
            player.sendSystemMessage(closeLine(session));
        }
    }

    private static MutableComponent choiceLine(DialogueSession session, int index, DialogueChoicePayload choice) {
        String prefix = "[" + index + "] ";
        MutableComponent line = Component.literal(prefix + choice.getText());
        boolean clickable = choice.isEnabled() && isCommandSafeIdentifier(choice.getId());
        line.withStyle(style -> choiceStyle(style, clickable, command("choose " + session.getSessionId() + " " + choice.getId())));
        return line;
    }

    private static MutableComponent closeLine(DialogueSession session) {
        MutableComponent line = Component.literal("[x] ").append(Component.translatable("geometry_node.dialogue.close"));
        line.withStyle(style -> choiceStyle(style, true, command("close " + session.getSessionId())));
        return line;
    }

    private static MutableComponent closeLine(DialogueSession session, String text, boolean enabled) {
        MutableComponent line = Component.literal("[x] ");
        if (text == null || text.isBlank() || "Close".equals(text)) {
            line.append(Component.translatable("geometry_node.dialogue.close"));
        } else {
            line.append(Component.literal(text));
        }
        line.withStyle(style -> choiceStyle(style, enabled, command("choose " + session.getSessionId() + " closed")));
        return line;
    }

    private static Style choiceStyle(Style style, boolean clickable, String command) {
        Style result = style.withColor(clickable ? ChatFormatting.AQUA : ChatFormatting.DARK_GRAY);
        if (clickable) {
            result = result.withUnderlined(true)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
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
