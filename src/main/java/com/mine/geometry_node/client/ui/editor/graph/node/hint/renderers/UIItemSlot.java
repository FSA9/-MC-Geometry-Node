package com.mine.geometry_node.client.ui.editor.graph.node.hint.renderers;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.components.valuepreview.ItemSlotView;
import com.mine.geometry_node.client.ui.editor.graph.node.hint.UIHintValueBinder;
import com.mine.geometry_node.client.ui.components.overlay.ItemStackTooltipOverlay;
import com.mine.geometry_node.client.ui.editor.graph.picker.VanillaInventoryPicker;
import com.mine.geometry_node.core.node.document.NodeData;
import com.mine.geometry_node.core.utils.ItemCodecUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.MotionEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

/** Editor adapter for the reusable item-stack preview. */
public class UIItemSlot extends ItemSlotView implements ViewportScaledHint, ViewportTransformedHint, InteractiveHintTarget {
    private final NodeData mNodeData;
    private final String mPortId;
    private final EditorContext mEditorContext;
    private String mLastJson;

    public UIItemSlot(Context context) {
        this(context, null, "", null);
    }

    public UIItemSlot(Context context, NodeData nodeData, String portId, EditorContext editorContext) {
        super(context);
        mNodeData = nodeData;
        mPortId = portId;
        mEditorContext = editorContext;
        setEditable(nodeData != null && editorContext != null);
        refreshDisplayValue();
    }

    @Override
    protected void refreshDisplayValue() {
        if (mNodeData == null) return;
        Object rawValue = mNodeData.inputs.get(mPortId);
        String json = rawValue instanceof String ? (String) rawValue : "";
        if (json.equals(mLastJson)) return;
        mLastJson = json;
        Minecraft minecraft = Minecraft.getInstance();
        setDisplayStack(minecraft.level != null
                ? ItemCodecUtils.fromJson(json, minecraft.level.registryAccess())
                : ItemStack.EMPTY);
    }

    @Override
    protected void onStackHover(MotionEvent event, ItemStack stack) {
        ItemStackTooltipOverlay.showForEvent(this, stack, event);
    }

    @Override
    protected void onStackHoverExit(ItemStack stack) {
        super.onStackHoverExit(stack);
        ItemStackTooltipOverlay.hide();
    }

    @Override
    protected void onOpenEditorRequested() {
        Minecraft mc = Minecraft.getInstance();
        if (mNodeData == null || mEditorContext == null || mc.player == null) return;

        ItemStackTooltipOverlay.hide();
        VanillaInventoryPicker.openItem(pickedStack -> {
            if (mc.level == null) return;
            UIHintValueBinder.commit(
                    mEditorContext,
                    mNodeData,
                    mPortId,
                    ItemCodecUtils.toJson(pickedStack, mc.level.registryAccess())
            );
            mLastJson = null;
            refreshDisplayValue();
            ItemStackTooltipOverlay.hide();
        }, this::requestFocus);
    }

    @Override
    protected void onPasteRequested(String json) {
        if (mEditorContext != null && mNodeData != null) {
            UIHintValueBinder.commit(mEditorContext, mNodeData, mPortId, json);
            mLastJson = null;
            refreshDisplayValue();
        }
    }
}
