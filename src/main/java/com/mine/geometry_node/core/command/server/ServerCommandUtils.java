package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.execution.storage.DynamicGraphManager;
import com.mine.geometry_node.core.execution.storage.GraphResourceManager;
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
}