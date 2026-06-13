package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.blueprint.debug.AreaDebugSessionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;

public final class GeometryNodeDebugCommand {
    private GeometryNodeDebugCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("geometry_node")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("area")
                                        .then(Commands.literal("on")
                                                .executes(context -> enable(context, AreaDebugSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enable(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disable)
                                        )
                                )
                        )
        );
    }

    private static int enable(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.enable(player, radius);
    }

    private static int disable(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.disable(player, true);
    }
}
