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

public class ServerGraphCommand {
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

        dispatcher.register(
                Commands.literal("bind_graph")
                        .requires(source -> source.hasPermission(2))

                        // --- 1.1 实体局部图 (target) ---
                        .then(Commands.literal("target")
                                .then(Commands.argument("targets", EntityArgument.entities())

                                        // 默认动作 (什么都不输入): /bind_graph target <targets> -> 列出绑定的图
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

                                        // 显式 list: /bind_graph target <targets> list -> 列出绑定的图
                                        .then(Commands.literal("list")
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

                                        // 绑定特定图: /bind_graph target <targets> <graph_id>
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

                        // --- 1.2 全局图 (global) ---
                        .then(Commands.literal("global")

                                // 默认动作 (什么都不输入): /bind_graph global -> 列出全局绑定的图
                                .executes(context -> {
                                    java.util.Set<String> graphs = GraphEngine.getGlobalBoundGraphs(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.literal(
                                            "全局绑定的图: " + (graphs.isEmpty() ? "无" : graphs)
                                    ), false);
                                    return 1;
                                })

                                // 显式 list: /bind_graph global list -> 列出全局绑定的图
                                .then(Commands.literal("list")
                                        .executes(context -> {
                                            java.util.Set<String> graphs = GraphEngine.getGlobalBoundGraphs(context.getSource().getLevel());
                                            context.getSource().sendSuccess(() -> Component.literal(
                                                    "全局绑定的图: " + (graphs.isEmpty() ? "无" : graphs)
                                            ), false);
                                            return 1;
                                        })
                                )

                                // 绑定特定图: /bind_graph global <graph_id>
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

        dispatcher.register(
                Commands.literal("unbind_graph")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("target")
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
                        .then(Commands.literal("global")
                                .executes(context -> {
                                    GraphEngine.unbindAllGlobalGraphs(context.getSource().getLevel());
                                    context.getSource().sendSuccess(() -> Component.literal("成功解绑全局服务器上的所有图。"), true);
                                    return 1;
                                })
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

        dispatcher.register(
                Commands.literal("list_graphs")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("server")
                                .executes(context -> {
                                    java.util.Set<String> allGraphs = new java.util.HashSet<>();
                                    allGraphs.addAll(GraphResourceManager.getInstance().getAllGraphIds());
                                    allGraphs.addAll(DynamicGraphManager.getAllDynamicGraphIds());

                                    if (allGraphs.isEmpty()) {
                                        context.getSource().sendSuccess(() -> Component.literal("§e[云端]§r 服务器上没有图纸。"), false);
                                    } else {
                                        String list = String.join("\n- ", allGraphs);
                                        context.getSource().sendSuccess(() -> Component.literal("§b[云端图纸列表]§r\n- " + list), false);
                                    }
                                    return allGraphs.size();
                                })
                        )
        );

        dispatcher.register(
                Commands.literal("download_graph")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("graph_id", ResourceLocationArgument.id())
                                .suggests(SUGGEST_GRAPHS)
                                .executes(context -> {
                                    net.minecraft.server.level.ServerPlayer player = context.getSource().getPlayerOrException();
                                    String graphId = ResourceLocationArgument.getId(context, "graph_id").toString();

                                    try {
                                        java.io.File folder = player.getServer().getWorldPath(DynamicGraphManager.GRAPH_DIR).toFile();
                                        java.io.File file = new java.io.File(folder, graphId + ".json");

                                        if (file.exists()) {
                                            String json = java.nio.file.Files.readString(file.toPath());
                                            com.mine.geometry_node.core.network.NetworkHandler.sendToPlayer(player,
                                                    new com.mine.geometry_node.core.network.packet.s2c.PacketSyncDownload(graphId, json));
                                            context.getSource().sendSuccess(() -> Component.literal("§a正在下发图纸: " + graphId), false);
                                        } else {
                                            context.getSource().sendFailure(Component.literal("找不到动态图纸文件，可能是内置数据包图纸。"));
                                        }
                                    } catch (Exception e) {
                                        context.getSource().sendFailure(Component.literal("服务器读取文件失败: " + e.getMessage()));
                                    }
                                    return 1;
                                })
                        )
        );
    }
}