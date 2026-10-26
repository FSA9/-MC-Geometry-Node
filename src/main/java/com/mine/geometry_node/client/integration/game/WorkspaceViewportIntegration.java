package com.mine.geometry_node.client.integration.game;

import com.mine.geometry_node.client.ui.theme.UiThemes;

import com.mine.geometry_node.client.runtime.game.host.ViewportHostPolicy;
import com.mine.geometry_node.client.runtime.game.render.ViewportFramePublications;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.client.feature.scene.SceneEditBackends;
import com.mine.geometry_node.client.ui.MainUI;
import icyllis.modernui.mc.MuiScreen;
import net.minecraft.client.Minecraft;

/** Concrete workspace wiring for input, frame composition and native scene subscriptions. */
public final class WorkspaceViewportIntegration {
    private WorkspaceViewportIntegration() {}

    public static void install() {
        ViewportFramePublications.installFactory(GameFrameImage.Channel::new);
        ViewportHostPolicy.install(screen -> {
            if (!(screen instanceof MuiScreen mui)) return ViewportHostPolicy.Snapshot.NONE;
            return mui.getFragment() instanceof MainUI
                    ? new ViewportHostPolicy.Snapshot(true, UiThemes.EDITOR.surface().root(), true)
                    : new ViewportHostPolicy.Snapshot(false, 0, true);
        });
        SceneEditBackends.configureNativeSubscription(() ->
                ViewportHostPolicy.snapshot(Minecraft.getInstance().screen).workspace()
                        && ViewportVisualization.requestedNativeEntities());
    }
}
