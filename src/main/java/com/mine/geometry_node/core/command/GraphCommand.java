package com.mine.geometry_node.core.command;

import com.mine.geometry_node.core.execution.storage.DynamicGraphManager;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mine.geometry_node.core.execution.GraphEngine;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

import java.util.Collection;

public class GraphCommand {
    // 动态图 ID 补全提供者
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_GRAPHS = (context, builder) -> {
        java.util.Set<String> allGraphs = new java.util.HashSet<>();

        // 静态数据包
        allGraphs.addAll(GraphResourceManager.getInstance().getAllGraphIds());
        // 动态上传图
        allGraphs.addAll(DynamicGraphManager.getAllDynamicGraphIds());

        return SharedSuggestionProvider.suggest(allGraphs, builder);
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // bind_graph
        dispatcher.register(
                Commands.literal("bind_graph")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("local")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .then(Commands.argument("graph_id", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_GRAPHS)
                                                .executes(context -> {
                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                                    String graphId = ResourceLocationArgument.getId(context, "graph_id").toString();

                                                    int count = 0;
                                                    for (Entity entity : targets) {
                                                        GraphEngine.bindGraph(entity, graphId);
                                                        count++;
                                                    }

                                                    int finalCount = count;
                                                    context.getSource().sendSuccess(() -> Component.literal("成功将图 " + graphId + " 绑定到 " + finalCount + " 个实体上。"), true);
                                                    return count;
                                                })
                                        )
                                )
                        )
                        .then(Commands.literal("global")
                                .then(Commands.argument("graph_id", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_GRAPHS)
                                        .executes(context -> {
                                            String graphId = ResourceLocationArgument.getId(context, "graph_id").toString();

                                            GraphEngine.bindGlobalGraph(context.getSource().getLevel(), graphId);

                                            context.getSource().sendSuccess(() -> Component.literal("成功将图 " + graphId + " 绑定到全局服务器。"), true);
                                            return 1;
                                        })
                                )
                        )
        );
        // unbind_graph
        dispatcher.register(
                Commands.literal("unbind_graph")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("local")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> {
                                            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                            for (Entity entity : targets) {
                                                GraphEngine.unbindAllGraphs(entity);
                                            }
                                            context.getSource().sendSuccess(() -> Component.literal("成功解绑 " + targets.size() + " 个实体上的所有图。"), true);
                                            return targets.size();
                                        })
                                        .then(Commands.argument("graph_id", ResourceLocationArgument.id())
                                                .suggests(SUGGEST_GRAPHS)
                                                .executes(context -> {
                                                    Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                                    String graphId = ResourceLocationArgument.getId(context, "graph_id").toString();
                                                    for (Entity entity : targets) {
                                                        GraphEngine.unbindGraph(entity, graphId);
                                                    }
                                                    context.getSource().sendSuccess(() -> Component.literal("成功解绑 " + targets.size() + " 个实体上的图: " + graphId), true);
                                                    return targets.size();
                                                })
                                        )
                                )
                        )
                        // 子指令 2: global
                        .then(Commands.literal("global")
                                // 2.1 不带参数：解绑全局所有图
                                .executes(context -> {
                                    GraphEngine.unbindAllGlobalGraphs(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.literal("成功解绑全局服务器上的所有图。"), true);
                                    return 1;
                                })
                                // 2.2 带参数：解绑全局指定图
                                .then(Commands.argument("graph_id", ResourceLocationArgument.id())
                                        .suggests(SUGGEST_GRAPHS)
                                        .executes(context -> {
                                            String graphId = ResourceLocationArgument.getId(context, "graph_id").toString();
                                            GraphEngine.unbindGlobalGraph(context.getSource().getLevel(), graphId);
                                            context.getSource().sendSuccess(() -> Component.literal("成功解绑全局服务器上的图: " + graphId), true);
                                            return 1;
                                        })
                                )
                        )
        );
        // list_graph
        dispatcher.register(
                Commands.literal("list_graph")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("local")
                                .then(Commands.argument("targets", EntityArgument.entities())
                                        .executes(context -> {
                                            Collection<? extends Entity> targets = EntityArgument.getEntities(context, "targets");
                                            for (Entity entity : targets) {
                                                java.util.Set<String> graphs = GraphEngine.getBoundGraphs(entity);
                                                context.getSource().sendSuccess(() -> Component.literal(
                                                        entity.getName().getString() + " 绑定的图: " + (graphs.isEmpty() ? "无" : graphs)
                                                ), false);
                                            }
                                            return targets.size();
                                        })
                                )
                        )
                        .then(Commands.literal("global")
                                .executes(context -> {
                                    java.util.Set<String> graphs = GraphEngine.getGlobalBoundGraphs(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "全局绑定的图: " + (graphs.isEmpty() ? "无" : graphs)
                                    ), false);
                                    return 1;
                                })
                        )
        );

    }
}