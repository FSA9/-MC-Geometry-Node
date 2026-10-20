package com.mine.geometry_node.client.integration.game;

import com.mine.geometry_node.client.runtime.game.host.ViewportHostPolicy;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.client.scene.SceneEditBackends;
import com.mine.geometry_node.client.ui.MainUI;
import com.mine.geometry_node.client.ui.UIConstants;
import icyllis.modernui.mc.MuiScreen;
import net.minecraft.client.Minecraft;

/** Concrete workspace wiring for input, frame composition and native scene subscriptions. */
public final class WorkspaceViewportIntegration {
    private WorkspaceViewportIntegration() {}

    public static void install() {
        ViewportHostPolicy.install(screen -> screen instanceof MuiScreen mui && mui.getFragment() instanceof MainUI
                ? new ViewportHostPolicy.Snapshot(true, UIConstants.MainUI.BG_ROOT)
                : ViewportHostPolicy.Snapshot.NONE);
        SceneEditBackends.configureNativeSubscription(() ->
                ViewportHostPolicy.snapshot(Minecraft.getInstance().screen).workspace()
                        && ViewportVisualization.requestedNativeEntities());
    }
}
