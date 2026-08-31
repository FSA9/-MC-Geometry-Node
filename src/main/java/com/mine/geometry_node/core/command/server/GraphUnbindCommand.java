package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class GraphUnbindCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("graph_unbind")
                        .requires(source -> source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER))

                        .then(Commands.literal("target")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> {
                                            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                            for (Entity entity : targets) {
                                                BlueprintRuntime.INSTANCE.unbindAllGraphs(entity);
                                            }
                                            context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_unbind.target_all", targets.size()), true);
                                            return targets.size();
                                        })
                                        .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                                .suggests(ServerCommandUtils.SUGGEST_BLUEPRINT_GRAPHS)
                                                .executes(context -> {
                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                                    String graphId = StringArgumentType.getString(context, "graph_id");
                                                    for (Entity entity : targets) {
                                                        BlueprintRuntime.INSTANCE.unbindGraph(entity, graphId);
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_unbind.target", targets.size(), graphId), true);
                                                    return targets.size();
                                                })
                                        )
                                )
                        )

                        .then(Commands.literal("global")
                                .executes(context -> {
                                    BlueprintRuntime.INSTANCE.unbindAllGlobalGraphs(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_unbind.global_all"), true);
                                    return 1;
                                })
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_BLUEPRINT_GRAPHS)
                                        .executes(context -> {
                                            String graphId = StringArgumentType.getString(context, "graph_id");
                                            BlueprintRuntime.INSTANCE.unbindGlobalGraph(context.getSource().getLevel(), graphId);
                                            context.getSource().sendSuccess(() -> Component.translatable("geometry_node.command.graph_unbind.global", graphId), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
