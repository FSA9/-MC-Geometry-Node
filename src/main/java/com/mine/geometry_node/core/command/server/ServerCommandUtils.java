package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.graph.storage.DynamicGraphManager;
import com.mine.geometry_node.core.engine.graph.storage.GraphResourceManager;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

import java.util.HashSet;
import java.util.Set;

public class ServerCommandUtils {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_GRAPHS = (context, builder) -> {
        Set<String> allGraphs = new HashSet<>();
        allGraphs.addAll(GraphResourceManager.getInstance().getAllGraphIds());
        allGraphs.addAll(DynamicGraphManager.getAllDynamicGraphIds());
        return SharedSuggestionProvider.suggest(allGraphs, builder);
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BLUEPRINT_GRAPHS = (context, builder) -> {
        Set<String> allGraphs = new HashSet<>();
        allGraphs.addAll(GraphResourceManager.getInstance().getGraphIds(GraphKind.BLUEPRINT));
        allGraphs.addAll(DynamicGraphManager.getDynamicGraphIds(GraphKind.BLUEPRINT));
        return SharedSuggestionProvider.suggest(allGraphs, builder);
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BEHAVIOR_TREES = (context, builder) -> {
        Set<String> graphs = new HashSet<>();
        graphs.addAll(GraphResourceManager.getInstance().getGraphIds(GraphKind.BEHAVIOR_TREE));
        graphs.addAll(DynamicGraphManager.getDynamicGraphIds(GraphKind.BEHAVIOR_TREE));
        return SharedSuggestionProvider.suggest(graphs, builder);
    };
}
