package com.mine.geometry_node.client.quest.ui;

import com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers.UIItemSlot;
import com.mine.geometry_node.core.engine.quest.model.QuestHintType;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

public final class QuestHintView extends UIItemSlot {
    public QuestHintView(Context context) {
        super(context);
    }

    public void setHint(QuestHintType type, String value) {
        setDisplayStack(resolveStack(type, value));
    }

    private static ItemStack resolveStack(QuestHintType type, String value) {
        Minecraft minecraft = Minecraft.getInstance();
        if (type == null || value == null || value.isBlank()) return ItemStack.EMPTY;
        if (type == QuestHintType.ITEM_STACK) {
            return minecraft.level != null
                    ? ItemCodecUtils.fromJson(value, minecraft.level.registryAccess())
                    : ItemStack.EMPTY;
        }
        if (type == QuestHintType.BLOCK) {
            Identifier id = Identifier.tryParse(value.trim());
            if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return ItemStack.EMPTY;
            Block block = BuiltInRegistries.BLOCK.getValue(id);
            return block != null ? new ItemStack(block.asItem()) : ItemStack.EMPTY;
        }
        return ItemStack.EMPTY;
    }
}
