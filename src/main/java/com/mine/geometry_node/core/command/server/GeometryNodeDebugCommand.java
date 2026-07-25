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
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))
                        .then(Commands.literal("debug")
                                .then(Commands.literal("area")
                                        .then(Commands.literal("on")
                                                .executes(context -> enableArea(context, AreaDebugSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enableArea(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disableArea)
                                        )
                                )
                                .then(Commands.literal("geometry")
                                        .then(Commands.literal("on")
                                                .executes(context -> enableGeometry(context, AreaDebugSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enableGeometry(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disableGeometry)
                                        )
                                )
                                .then(Commands.literal("schem")
                                        .then(Commands.literal("on")
                                                .executes(context -> enableSchematic(context, AreaDebugSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enableSchematic(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disableSchematic)
                                        )
                                )
                        )
        );
    }

    private static int enableArea(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.enableArea(player, radius);
    }

    private static int disableArea(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.disableArea(player, true);
    }

    private static int enableSchematic(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.enableSchematic(player, radius);
    }

    private static int disableSchematic(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.disableSchematic(player, true);
    }

    private static int enableGeometry(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.enableGeometry(player, radius);
    }

    private static int disableGeometry(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return AreaDebugSessionManager.disableGeometry(player, true);
    }
}
