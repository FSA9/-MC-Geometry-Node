package com.mine.geometry_node.client.ui;

import com.mine.geometry_node.client.ui.shell.MainUiShell;
import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import icyllis.modernui.mc.ScreenCallback;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeRegistry;
import icyllis.modernui.ModernUI;
import icyllis.modernui.audio.AudioManager;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.LayoutInflater;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

public class MainUI extends Fragment implements ScreenCallback {
    private MainUiShell shell;

    @Override
    public boolean isPauseScreen() {
        return !GameViewportInput.hasVisibleViews()
                && !com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime.hasVisibleViews();
    }

    @Override
    public boolean hasDefaultBackground() { return false; }

    @Override
    public boolean shouldBlurBackground() { return false; }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        UIUtils.syncFixedDensity();
        shell = new MainUiShell(getContext());
        return shell;
    }

    @Override
    public void onDestroyView() {
        GameViewportInput.releaseAll();
        if (shell != null) {
            shell.destroy();
            shell = null;
        }
        super.onDestroyView();
    }

    public static void main(String[] args) {
        System.setProperty("java.awt.headless", "true");
        System.setProperty("gn.standalone", "true");
        Configurator.setRootLevel(Level.DEBUG);

        com.mine.geometry_node.client.config.ConfigManager.INSTANCE.initOrLoad();
        NodeRegistry.INSTANCE.init();

        try (ModernUI app = new ModernUI()) {
            app.run(new MainUI());
        }
        AudioManager.getInstance().close();
        System.gc();
    }
}
