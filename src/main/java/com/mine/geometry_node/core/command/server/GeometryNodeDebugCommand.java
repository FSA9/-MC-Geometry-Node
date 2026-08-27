package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.blueprint.debug.DebugRendererSessionManager;
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
                                .then(Commands.literal("on")
                                        .executes(context -> enableAll(context, DebugRendererSessionManager.DEFAULT_RADIUS))
                                        .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                .executes(context -> enableAll(context, DoubleArgumentType.getDouble(context, "radius")))
                                        )
                                )
                                .then(Commands.literal("off")
                                        .executes(GeometryNodeDebugCommand::disableAll)
                                )
                                .then(Commands.literal("area")
                                        .then(Commands.literal("on")
                                                .executes(context -> enableArea(context, DebugRendererSessionManager.DEFAULT_RADIUS))
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
                                                .executes(context -> enableGeometry(context, DebugRendererSessionManager.DEFAULT_RADIUS))
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
                                                .executes(context -> enableSchematic(context, DebugRendererSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enableSchematic(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disableSchematic)
                                        )
                                )
                                .then(Commands.literal("interaction")
                                        .then(Commands.literal("on")
                                                .executes(context -> enableInteraction(context, DebugRendererSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enableInteraction(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disableInteraction)
                                        )
                                )
                                .then(Commands.literal("pathfinding")
                                        .then(Commands.literal("on")
                                                .executes(context -> enablePathfinding(context, DebugRendererSessionManager.DEFAULT_RADIUS))
                                                .then(Commands.argument("radius", DoubleArgumentType.doubleArg(1.0D, 2048.0D))
                                                        .executes(context -> enablePathfinding(context, DoubleArgumentType.getDouble(context, "radius")))
                                                )
                                        )
                                        .then(Commands.literal("off")
                                                .executes(GeometryNodeDebugCommand::disablePathfinding)
                                        )
                                )
                        )
        );
    }

    private static int enableAll(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return DebugRendererSessionManager.enableAll(context.getSource().getPlayerOrException(), radius);
    }

    private static int disableAll(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return DebugRendererSessionManager.disableAll(context.getSource().getPlayerOrException(), true);
    }

    private static int enableArea(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.enableArea(player, radius);
    }

    private static int disableArea(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.disableArea(player, true);
    }

    private static int enableSchematic(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.enableSchematic(player, radius);
    }

    private static int disableSchematic(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.disableSchematic(player, true);
    }

    private static int enableGeometry(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.enableGeometry(player, radius);
    }

    private static int disableGeometry(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.disableGeometry(player, true);
    }

    private static int enableInteraction(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.enableInteraction(player, radius);
    }

    private static int disableInteraction(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        return DebugRendererSessionManager.disableInteraction(player, true);
    }

    private static int enablePathfinding(CommandContext<CommandSourceStack> context, double radius)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return DebugRendererSessionManager.enablePathfinding(context.getSource().getPlayerOrException(), radius);
    }

    private static int disablePathfinding(CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return DebugRendererSessionManager.disablePathfinding(context.getSource().getPlayerOrException(), true);
    }
}
