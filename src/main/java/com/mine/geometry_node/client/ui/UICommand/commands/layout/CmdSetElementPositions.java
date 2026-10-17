package com.mine.geometry_node.client.ui.UICommand.commands.layout;

import com.mine.geometry_node.client.ui.UICommand.ICommand;
import com.mine.geometry_node.client.ui.editor.graph.GraphController;
import com.mine.geometry_node.core.node.document.FrameData;

import java.util.HashMap;
import java.util.Map;

public class CmdSetElementPositions implements ICommand {
    private final GraphController mController;
    private final Map<String, float[]> mNewNodePos = new HashMap<>();
    private final Map<String, float[]> mNewFramePos = new HashMap<>();

    public CmdSetElementPositions(GraphController controller, Map<String, float[]> nodePositions, Map<String, float[]> framePositions) {
        this.mController = controller;

        for (Map.Entry<String, float[]> entry : nodePositions.entrySet()) {
            float[] oldPos = controller.getNodePosition(entry.getKey());
            float[] newPos = entry.getValue();
            if (oldPos != null && newPos != null && newPos.length >= 2) {
                mNewNodePos.put(entry.getKey(), new float[]{newPos[0], newPos[1]});
            }
        }

        for (Map.Entry<String, float[]> entry : framePositions.entrySet()) {
            FrameData frame = controller.getContext().getGraph().getFrame(entry.getKey());
            float[] newPos = entry.getValue();
            if (frame != null && newPos != null && newPos.length >= 2) {
                mNewFramePos.put(entry.getKey(), new float[]{newPos[0], newPos[1]});
            }
        }
    }

    @Override
    public void execute() {
        for (Map.Entry<String, float[]> entry : mNewNodePos.entrySet()) {
            mController.setNodePosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
        for (Map.Entry<String, float[]> entry : mNewFramePos.entrySet()) {
            mController.setFramePosition(entry.getKey(), entry.getValue()[0], entry.getValue()[1]);
        }
    }

}
