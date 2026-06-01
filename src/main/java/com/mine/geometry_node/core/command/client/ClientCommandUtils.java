package com.mine.geometry_node.core.command.client;

import com.mine.geometry_node.client.ui.persistence.LocalDraftManager;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;

public class ClientCommandUtils {
    public static <S> SuggestionProvider<S> getLocalSuggestions() {
        return (context, builder) -> SharedSuggestionProvider.suggest(LocalDraftManager.getAllDraftNames(), builder);
    }

    public static void sendClientMsg(String message) {
        if (Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.displayClientMessage(Component.literal(message), false);
        }
    }
}