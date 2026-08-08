package com.mine.geometry_node.client.quest.ui;

import com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers.UIItemSlot;
import com.mine.geometry_node.client.ui.viewport.node.UIHints.renderers.UIEntityTemplatePreview;
import com.mine.geometry_node.core.engine.system.quest.model.QuestHintType;
import com.mine.geometry_node.core.node.value.EntityTemplateValue;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.function.Consumer;

public final class QuestHintView extends FrameLayout {
    private final UIItemSlot itemView;
    private final UIEntityTemplatePreview entityView;
    private QuestHintType hintType = QuestHintType.NONE;

    public QuestHintView(Context context) {
        super(context);
        setClipChildren(false);

        itemView = new UIItemSlot(context);
        addView(itemView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        entityView = new UIEntityTemplatePreview(
                context, null, "", null, UIEntityTemplatePreview.RotationMode.HORIZONTAL);
        entityView.setVisibility(View.GONE);
        addView(entityView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void setHint(QuestHintType type, String value) {
        hintType = type != null ? type : QuestHintType.NONE;
        boolean entity = hintType == QuestHintType.ENTITY;
        itemView.setVisibility(entity ? View.GONE : View.VISIBLE);
        entityView.setVisibility(entity ? View.VISIBLE : View.GONE);
        itemView.setDisplayStack(entity ? ItemStack.EMPTY : resolveStack(hintType, value));
        entityView.setDisplayTemplate(entity ? EntityTemplateValue.from(value) : EntityTemplateValue.EMPTY);
    }

    public void setDisplayClickAction(Runnable action) {
        itemView.setDisplayClickAction(action);
        entityView.setDisplayClickAction(action);
    }

    public void setDisplayPasteAction(Consumer<String> action) {
        itemView.setDisplayPasteAction(action);
    }

    public void setEntityDisplayPasteAction(Consumer<EntityTemplateValue> action) {
        entityView.setDisplayPasteAction(action);
    }

    public void requestHintFocus() {
        if (hintType == QuestHintType.ENTITY) {
            entityView.requestFocus();
        } else {
            itemView.requestFocus();
        }
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
