package com.mine.geometry_node.client.ui.viewport.selection;

import com.mine.geometry_node.client.ui.viewport.frame.FrameVisualAdapter;
import com.mine.geometry_node.client.ui.viewport.node.NodeVisualAdapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ViewportSelection {
    private final LinkedHashSet<String> mSelectedNodeIds = new LinkedHashSet<>();
    private final LinkedHashSet<String> mSelectedFrameIds = new LinkedHashSet<>();

    public void clear() {
        mSelectedNodeIds.clear();
        mSelectedFrameIds.clear();
    }

    public void clearFrames() {
        mSelectedFrameIds.clear();
    }

    public boolean isEmpty() {
        return mSelectedNodeIds.isEmpty() && mSelectedFrameIds.isEmpty();
    }

    public boolean containsNode(String nodeId) {
        return nodeId != null && mSelectedNodeIds.contains(nodeId);
    }

    public boolean containsFrame(String frameId) {
        return frameId != null && mSelectedFrameIds.contains(frameId);
    }

    public void selectNode(NodeVisualAdapter node) {
        if (node == null || node.getNodeId() == null) return;
        mSelectedNodeIds.remove(node.getNodeId());
        mSelectedNodeIds.add(node.getNodeId());
    }

    public void selectFrame(FrameVisualAdapter frame) {
        if (frame == null || frame.getFrameId() == null) return;
        mSelectedFrameIds.remove(frame.getFrameId());
        mSelectedFrameIds.add(frame.getFrameId());
    }

    public void removeNode(String nodeId) {
        if (nodeId != null) {
            mSelectedNodeIds.remove(nodeId);
        }
    }

    public void removeFrame(String frameId) {
        if (frameId != null) {
            mSelectedFrameIds.remove(frameId);
        }
    }

    public void setNodes(Collection<String> nodeIds) {
        mSelectedNodeIds.clear();
        if (nodeIds == null) return;
        for (String nodeId : nodeIds) {
            if (nodeId != null) {
                mSelectedNodeIds.add(nodeId);
            }
        }
    }

    public void setFrames(Collection<String> frameIds) {
        mSelectedFrameIds.clear();
        if (frameIds == null) return;
        for (String frameId : frameIds) {
            if (frameId != null) {
                mSelectedFrameIds.add(frameId);
            }
        }
    }

    public void syncSessionLists(List<String> selectedNodeIds, List<String> selectedFrameIds) {
        syncList(selectedNodeIds, mSelectedNodeIds);
        syncList(selectedFrameIds, mSelectedFrameIds);
    }

    private void syncList(List<String> target, Set<String> source) {
        if (target == null) return;
        target.clear();
        target.addAll(source);
    }

    public List<String> nodeIds() {
        return new ArrayList<>(mSelectedNodeIds);
    }

    public List<String> frameIds() {
        return new ArrayList<>(mSelectedFrameIds);
    }
}
