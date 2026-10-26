package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import net.minecraft.network.chat.Component;

/** Presentation vocabulary shared by global and entity faction editors. */
public final class FactionUi {
    private FactionUi() {}
    public static int px(float value) { return UIUtils.dp2pxInt(value); }
    public static String tr(String key, Object... arguments) {
        return Component.translatable("geometry_node.faction." + key, arguments).getString();
    }
    public static String errorMessage(Throwable failure) {
        if (failure == null) return "";
        while ((failure instanceof java.util.concurrent.CompletionException
                || failure instanceof java.util.concurrent.ExecutionException) && failure.getCause() != null)
            failure = failure.getCause();
        String key = failure instanceof IllegalArgumentException
                ? "geometry_node.faction.error.invalid" : failure.getMessage();
        return errorMessage(key == null || !key.startsWith("geometry_node.")
                ? "geometry_node.faction.error.failed" : key);
    }
    public static String errorMessage(String key) {
        return key == null || key.isEmpty() ? "" : Component.translatable(key).getString();
    }
    public static ShapeDrawable fill(int color) {
        ShapeDrawable drawable = new ShapeDrawable(); drawable.setColor(color); return drawable;
    }
}
