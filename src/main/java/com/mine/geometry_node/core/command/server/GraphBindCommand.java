package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.Set;

public class GraphBindCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("graph_bind")
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))

                        // 1. target 模式
                        .then(Commands.literal("target")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> handleTargetList(context))
                                        .then(Commands.literal("list")
                                                .executes(context -> handleTargetList(context))
                                        )
                                        .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                                .suggests(ServerCommandUtils.SUGGEST_BLUEPRINT_GRAPHS)
                                                .executes(context -> handleTargetBind(context))
                                        )
                                )
                        )

                        // 2. global 模式
                        .then(Commands.literal("global")
                                .executes(context -> handleGlobalList(context))
                                .then(Commands.literal("list")
                                        .executes(context -> handleGlobalList(context))
                                )
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_BLUEPRINT_GRAPHS)
                                        .executes(context -> handleGlobalBind(context))
                                )
                        )

                        // 3. look 模式
                        .then(Commands.literal("look")
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_BLUEPRINT_GRAPHS)
                                        .executes(context -> handleLookBind(context))
                                )
                        )
        );
    }

    private static int handleTargetList(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        for (Entity entity : targets) {
            Set<String> graphs = BlueprintRuntime.INSTANCE.getBoundGraphs(entity);
            context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_bind.target_list",
                    entity.getName().getString(), graphs.isEmpty() ? "无" : graphs), false);
        }
        return targets.size();
    }

    private static int handleTargetBind(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
        String graphId = StringArgumentType.getString(context, "graph_id");
        int count = 0;
        for (Entity entity : targets) {
            BlueprintRuntime.INSTANCE.bindGraph(entity, graphId);
            count++;
        }
        int finalCount = count;
        context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_bind.target_bound", graphId, finalCount), true);
        return count;
    }

    private static int handleGlobalList(CommandContext<CommandSourceStack> context) {
        Set<String> graphs = BlueprintRuntime.INSTANCE.getGlobalBoundGraphs(context.getSource().getLevel());
        context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_bind.global_list",
                graphs.isEmpty() ? "无" : graphs), false);
        return 1;
    }

    private static int handleGlobalBind(CommandContext<CommandSourceStack> context) {
        String graphId = StringArgumentType.getString(context, "graph_id");
        BlueprintRuntime.INSTANCE.bindGlobalGraph(context.getSource().getLevel(), graphId);
        context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_bind.global_bound", graphId), true);
        return 1;
    }

    private static int handleLookBind(CommandContext<CommandSourceStack> context) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = context.getSource().getPlayerOrException();
        String graphId = StringArgumentType.getString(context, "graph_id");

        double reachDistance = 5.0;
        Vec3 eyePosition = player.getEyePosition();
        Vec3 lookVector = player.getViewVector(1.0F);
        Vec3 traceEnd = eyePosition.add(lookVector.x * reachDistance, lookVector.y * reachDistance, lookVector.z * reachDistance);

        AABB searchBox = player.getBoundingBox().expandTowards(lookVector.scale(reachDistance)).inflate(1.0D, 1.0D, 1.0D);

        EntityHitResult hitResult = ProjectileUtil.getEntityHitResult(
                player, eyePosition, traceEnd, searchBox,
                entity -> !entity.isSpectator() && entity.isPickable(),
                reachDistance * reachDistance
        );

        if (hitResult != null && hitResult.getEntity() != null) {
            Entity targetEntity = hitResult.getEntity();
            BlueprintRuntime.INSTANCE.bindGraph(targetEntity, graphId);
            context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_bind.look_bound",
                    graphId, targetEntity.getName().getString()), true);
            return 1;
        } else {
            context.getSource().sendFailure(Component.translatable("geometry_node.command.graph_bind.no_entity"));
            return 0;
        }
    }
}
