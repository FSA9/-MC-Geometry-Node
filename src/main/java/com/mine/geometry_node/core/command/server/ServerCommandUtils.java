package com.mine.geometry_node.core.command.server;

import com.mine.geometry_node.core.engine.graph.storage.GraphAssetLifecycleIndex;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;

public class ServerCommandUtils {
    public static final SuggestionProvider<CommandSourceStack> SUGGEST_GRAPHS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(
                GraphAssetLifecycleIndex.INSTANCE.getGraphIds(), builder);
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BLUEPRINT_GRAPHS = (context, builder) -> {
        return SharedSuggestionProvider.suggest(
                GraphAssetLifecycleIndex.INSTANCE.getGraphIds(GraphKind.BLUEPRINT), builder);
    };

    public static final SuggestionProvider<CommandSourceStack> SUGGEST_BEHAVIOR_TREES = (context, builder) -> {
        return SharedSuggestionProvider.suggest(
                GraphAssetLifecycleIndex.INSTANCE.getGraphIds(GraphKind.BEHAVIOR_TREE), builder);
    };
}
