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
                        .requires(source -> source.hasPermission(2))

                        .then(Commands.literal("target")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> {
                                            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                            for (Entity entity : targets) {
                                                BlueprintRuntime.INSTANCE.unbindAllGraphs(entity);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("成功解绑 " + targets.size() + " 个实体上的所有图。"), true);
                                            return targets.size();
                                        })
                                        .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                                .suggests(ServerCommandUtils.SUGGEST_GRAPHS)
                                                .executes(context -> {
                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                                    String graphId = StringArgumentType.getString(context, "graph_id");
                                                    for (Entity entity : targets) {
                                                        BlueprintRuntime.INSTANCE.unbindGraph(entity, graphId);
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal("成功解绑 " + targets.size() + " 个实体上的图: " + graphId), true);
                                                    return targets.size();
                                                })
                                        )
                                )
                        )

                        .then(Commands.literal("global")
                                .executes(context -> {
                                    BlueprintRuntime.INSTANCE.unbindAllGlobalGraphs(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.literal("成功解绑全局服务器上的所有图。"), true);
                                    return 1;
                                })
                                .then(Commands.argument("graph_id", StringArgumentType.greedyString())
                                        .suggests(ServerCommandUtils.SUGGEST_GRAPHS)
                                        .executes(context -> {
                                            String graphId = StringArgumentType.getString(context, "graph_id");
                                            BlueprintRuntime.INSTANCE.unbindGlobalGraph(context.getSource().getLevel(), graphId);
                                            context.getSource().sendSuccess(() -> Component.literal("成功解绑全局服务器上的图: " + graphId), true);
                                            return 1;
                                        })
                                )
                        )
        );
    }
}
