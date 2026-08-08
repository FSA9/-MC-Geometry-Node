package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.system.dialogue.DialogueRuntime;
import com.mine.geometry_node.core.engine.system.dialogue.presenter.ChatDialoguePresenter;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public class DialogueCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal(ChatDialoguePresenter.COMMAND_ROOT)
                        .then(Commands.literal(ChatDialoguePresenter.COMMAND_DIALOGUE)
                                .then(Commands.literal("choose")
                                        .then(Commands.argument("session_id", UuidArgument.uuid())
                                                .then(Commands.argument("choice_id", StringArgumentType.word())
                                                        .executes(context -> choose(
                                                                context.getSource(),
                                                                UuidArgument.getUuid(context, "session_id"),
                                                                StringArgumentType.getString(context, "choice_id")
                                                        ))
                                                )
                                        )
                                        .then(Commands.argument("choice_id", StringArgumentType.word())
                                                .executes(context -> choose(context.getSource(), StringArgumentType.getString(context, "choice_id")))
                                        )
                                )
                                .then(Commands.literal("close")
                                        .then(Commands.argument("session_id", UuidArgument.uuid())
                                                .executes(context -> close(context.getSource(), UuidArgument.getUuid(context, "session_id")))
                                        )
                                        .executes(context -> close(context.getSource()))
                                )
                        )
        );
    }

    private static int choose(CommandSourceStack source, UUID sessionId, String choiceId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (DialogueRuntime.INSTANCE.choose(player, sessionId, choiceId) == null) {
            source.sendFailure(Component.literal("没有可用的对话选项。"));
            return 0;
        }
        return 1;
    }

    private static int choose(CommandSourceStack source, String choiceId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (DialogueRuntime.INSTANCE.chooseCurrent(player, choiceId) == null) {
            source.sendFailure(Component.literal("没有可用的对话选项。"));
            return 0;
        }
        return 1;
    }

    private static int close(CommandSourceStack source, UUID sessionId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (DialogueRuntime.INSTANCE.closeFromClient(player, sessionId) == null) {
            source.sendFailure(Component.literal("没有可关闭的对话。"));
            return 0;
        }
        return 1;
    }

    private static int close(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (DialogueRuntime.INSTANCE.closeCurrentFromClient(player) == null) {
            source.sendFailure(Component.literal("没有可关闭的对话。"));
            return 0;
        }
        return 1;
    }
}
