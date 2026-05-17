package com.mine.geometry_node.client.ui.viewport;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.core.node.Connection;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeRegistry;
import com.mine.geometry_node.core.node.nodes.NodeDef;
import com.mine.geometry_node.core.node.port.PortRow;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GraphController {
    private final EditorContext mContext;

    public GraphController(EditorContext context) {
        this.mContext = context;
    }

    public EditorContext getContext() {
        return mContext;
    }

    public void addNode(NodeData node) {
        mContext.getGraph().addNode(node);
        mContext.notifyNodeAdded(node);
    }

    public void removeNode(String nodeId) {
        mContext.getGraph().removeNode(nodeId);
        mContext.notifyNodeRemoved(nodeId);
    }

    public float[] getNodePosition(String nodeId) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        return node != null ? node.uiPos : null;
    }

    public void setNodePosition(String nodeId, float x, float y) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node != null) {
            node.setPosition(x, y);
            mContext.notifyNodeMoved(nodeId, x, y);

            if (node.parentFrame != null) {
                updateFrameBounds(node.parentFrame);
            }
        }
    }

    public void addConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.addDataConnection(outPortId, inNodeId, inPortId);

            NodeData inNode = mContext.getGraph().getNode(inNodeId);
            if (inNode != null) {
                inNode.inputs.remove(inPortId);
                inNode.setInputConnected(inPortId, true);
            }

            mContext.notifyConnectionAdded(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void removeConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.removeDataConnection(outPortId, inNodeId, inPortId);

            NodeData inNode = mContext.getGraph().getNode(inNodeId);
            if (inNode != null) {
                inNode.setInputConnected(inPortId, false);
            }

            mContext.notifyConnectionRemoved(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void addExecutionConnection(String outNodeId, String outPortId, String inNodeId, String inPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            outNode.addExecutionConnection(outPortId, inNodeId, inPortId);
            mContext.notifyExecutionConnectionAdded(outNodeId, outPortId, inNodeId, inPortId);
        }
    }

    public void removeExecutionConnection(String outNodeId, String outPortId) {
        NodeData outNode = mContext.getGraph().getNode(outNodeId);
        if (outNode != null) {
            Connection c = outNode.execOutputs.get(outPortId);
            if (c != null) {
                outNode.removeExecutionConnection(outPortId);
                mContext.notifyExecutionConnectionRemoved(outNodeId, outPortId, c.targetNodeId(), c.targetPortName());
            }
        }
    }

    // 【核心重构】吸收了原 setNodeProperty 的全部清理逻辑
    public void setNodeInputValue(String nodeId, String portId, Object value) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        // 1. 更新节点输入值 (不论是物理连线端口还是纯属性端口，统统存这里)
        if (value == null) {
            node.inputs.remove(portId);
        } else {
            node.inputs.put(portId, value);
        }

        // 2. 获取更新属性后的新 NodeDef (防止这个输入值是动态分支数量控制参数)
        NodeDef newDef = NodeRegistry.INSTANCE.resolveDefinition(node);
        if (newDef == null) return;

        // 3. 提取新定义中所有【仍然合法】的端口 ID
        Set<String> validInputs = new HashSet<>();
        Set<String> validOutputs = new HashSet<>();
        for (PortRow row : newDef.rows()) {
            if (row.leftPort() != null) validInputs.add(row.leftPort().id());
            if (row.rightPort() != null) validOutputs.add(row.rightPort().id());
        }

        // 4. 清理当前节点失效的【输出】连线
        List<String> invalidOutPorts = new ArrayList<>();
        for (String outPort : node.outputs.keySet()) {
            if (!validOutputs.contains(outPort)) {
                invalidOutPorts.add(outPort);
            }
        }
        for (String invalidOut : invalidOutPorts) {
            List<Connection> links = new ArrayList<>(node.getConnections(invalidOut));
            for (Connection link : links) {
                removeConnection(nodeId, invalidOut, link.targetNodeId(), link.targetPortName());
            }
        }

        // 5. 清理当前节点失效的【执行流】连线
        List<String> invalidExecPorts = new ArrayList<>();
        for (String execPort : node.execOutputs.keySet()) {
            if (!validOutputs.contains(execPort)) {
                invalidExecPorts.add(execPort);
            }
        }
        for (String invalidExec : invalidExecPorts) {
            removeExecutionConnection(nodeId, invalidExec);
        }

        // 6. 清理其他节点连接到当前节点失效【输入】端口的连线
        for (NodeData otherNode : mContext.getGraph().nodes.values()) {
            for (String otherOutPort : new ArrayList<>(otherNode.outputs.keySet())) {
                List<Connection> links = new ArrayList<>(otherNode.getConnections(otherOutPort));
                for (Connection link : links) {
                    if (link.targetNodeId().equals(nodeId) && !validInputs.contains(link.targetPortName())) {
                        removeConnection(otherNode.id, otherOutPort, nodeId, link.targetPortName());
                    }
                }
            }
        }

        // 7. 通知 viewport 重新构建该节点的 UI
        mContext.notifyNodeStructureChanged(node);
    }

    public boolean isInputPortConnected(String targetNodeId, String targetPortId) {
        NodeData node = mContext.getGraph().getNode(targetNodeId);
        if (node == null) return false;
        return node.connectedInputs.contains(targetPortId);
    }

    // 【核心清理】移除所有对 properties 的操作
    public void removeDynamicBranch(String nodeId, String propertyKey, int removeIndex, int totalCount) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        if (removeIndex < 1 || removeIndex > totalCount) return;

        for (int i = removeIndex; i < totalCount; i++) {
            String oldSuffix = "_" + (i + 1);
            String newSuffix = "_" + i;
            shiftMapData(node.inputs, oldSuffix, newSuffix);
            shiftMapData(node.outputs, oldSuffix, newSuffix);
            shiftMapData(node.execOutputs, oldSuffix, newSuffix);
            shiftConnections(nodeId, oldSuffix, newSuffix);
        }

        String lastSuffix = "_" + totalCount;
        node.inputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.outputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.execOutputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        shiftConnections(nodeId, lastSuffix, null);

        setNodeInputValue(nodeId, propertyKey, totalCount - 1);
    }

    private <V> void shiftMapData(java.util.Map<String, V> map, String oldSuffix, String newSuffix) {
        map.keySet().removeIf(k -> k.endsWith(newSuffix));

        java.util.Map<String, V> toMove = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, V> entry : map.entrySet()) {
            if (entry.getKey().endsWith(oldSuffix)) {
                String newKey = entry.getKey().substring(0, entry.getKey().length() - oldSuffix.length()) + newSuffix;
                toMove.put(newKey, entry.getValue());
            }
        }

        map.keySet().removeIf(k -> k.endsWith(oldSuffix));
        map.putAll(toMove);
    }

    private void shiftConnections(String targetNodeId, String oldSuffix, String newSuffix) {
        List<Runnable> connectionUpdates = new ArrayList<>();

        for (NodeData otherNode : mContext.getGraph().nodes.values()) {
            for (String outPort : otherNode.outputs.keySet()) {
                for (Connection link : otherNode.getConnections(outPort)) {
                    if (link.targetNodeId().equals(targetNodeId) && link.targetPortName().endsWith(oldSuffix)) {
                        connectionUpdates.add(() -> {
                            removeConnection(otherNode.id, outPort, targetNodeId, link.targetPortName());
                            if (newSuffix != null) {
                                String newPortName = link.targetPortName().substring(0, link.targetPortName().length() - oldSuffix.length()) + newSuffix;
                                addConnection(otherNode.id, outPort, targetNodeId, newPortName);
                            }
                        });
                    }
                }
            }
        }

        for (Runnable r : connectionUpdates) {
            r.run();
        }
    }

    // --- 新增：修改端口自定义名称 ---
    public void setPortCustomName(String nodeId, String category, String portId, String newName) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        // 根据 category 获取对应的 Map
        java.util.Map<String, NodeData.PortConfig> targetMap = switch (category) {
            case "inputs" -> node.portSettings.inputs;
            case "execInputs" -> node.portSettings.execInputs;
            case "execOutputs" -> node.portSettings.execOutputs;
            case "outputs" -> node.portSettings.outputs;
            default -> null;
        };

        if (targetMap != null) {
            NodeData.PortConfig config = targetMap.get(portId);
            if (config == null) {
                config = new NodeData.PortConfig();
                targetMap.put(portId, config);
            }

            // 如果新名字为空，也可以选择删除这个配置或者置空，这里选择保留对象更新属性
            config.customName = newName;

            // 通知重新构建节点结构，这会自动刷新 UI 上的文字并重新计算排版宽度
            mContext.notifyNodeStructureChanged(node);
        }
    }

    private static final float FRAME_PADDING_P = 20f;
    private static final float FRAME_MARGIN_K = 10f;
    private static final float FRAME_HEADER_H1 = 30f; // 标题栏高度预留

    public void addFrame(com.mine.geometry_node.core.node.FrameData frame) {
        mContext.getGraph().addFrame(frame);
        for (EditorContext.EditorListener l : mContext.getListeners()) {
            l.onFrameAdded(frame);
        }
    }

    public void removeFrame(String frameId) {
        mContext.getGraph().removeFrame(frameId);
        for (EditorContext.EditorListener l : mContext.getListeners()) {
            l.onFrameRemoved(frameId);
        }
    }

    /**
     * 核心：更新元素的父图框，并触发对应图框的边界重算
     */
    public void setElementParentFrame(String elementId, boolean isNode, String newParentFrameId) {
        String oldParentId = null;
        if (isNode) {
            NodeData node = mContext.getGraph().getNode(elementId);
            if (node != null) {
                oldParentId = node.parentFrame;
                node.parentFrame = newParentFrameId;
            }
        } else {
            com.mine.geometry_node.core.node.FrameData frame = mContext.getGraph().getFrame(elementId);
            if (frame != null) {
                oldParentId = frame.parentFrame;
                frame.parentFrame = newParentFrameId;
            }
        }

        // 重新计算受影响图框的边界
        if (oldParentId != null && !oldParentId.equals(newParentFrameId)) {
            updateFrameBounds(oldParentId);
        }
        if (newParentFrameId != null) {
            updateFrameBounds(newParentFrameId);
        }
    }

    /**
     * 核心：根据内部节点重新计算图框边界 (Auto-Bounding Box)
     */
    public void updateFrameBounds(String frameId) {
        com.mine.geometry_node.core.node.FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame == null) return;

        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean hasChildren = false;

        // 1. 遍历所有节点
        for (NodeData node : mContext.getGraph().nodes.values()) {
            if (frameId.equals(node.parentFrame)) {
                hasChildren = true;
                minX = Math.min(minX, node.uiPos[0]);
                minY = Math.min(minY, node.uiPos[1]);

                // 需要你在 NodeData 中补充 uiSize 字段，并在 UINode 排版后更新它
                float w = (node.uiSize != null && node.uiSize[0] > 0) ? node.uiSize[0] : 150f; // 150f为fallback
                float h = (node.uiSize != null && node.uiSize[1] > 0) ? node.uiSize[1] : 100f; // 100f为fallback

                maxX = Math.max(maxX, node.uiPos[0] + w);
                maxY = Math.max(maxY, node.uiPos[1] + h);
            }
        }

        // 2. 遍历所有子图框 (支持嵌套)
        for (com.mine.geometry_node.core.node.FrameData childFrame : mContext.getGraph().frames.values()) {
            if (frameId.equals(childFrame.parentFrame)) {
                hasChildren = true;
                minX = Math.min(minX, childFrame.uiPos[0]);
                minY = Math.min(minY, childFrame.uiPos[1]);
                maxX = Math.max(maxX, childFrame.uiPos[0] + childFrame.uiSize[0]);
                maxY = Math.max(maxY, childFrame.uiPos[1] + childFrame.uiSize[1]);
            }
        }

        // 3. 应用计算结果
        if (hasChildren) {
            float newX = minX - FRAME_PADDING_P - FRAME_MARGIN_K;
            // 顶部额外减去标题栏高度，保证标题栏在 P 区域内起步
            float newY = minY - FRAME_PADDING_P - FRAME_MARGIN_K - FRAME_HEADER_H1;
            float newW = (maxX - minX) + 2 * (FRAME_PADDING_P + FRAME_MARGIN_K);
            float newH = (maxY - minY) + 2 * (FRAME_PADDING_P + FRAME_MARGIN_K) + FRAME_HEADER_H1;

            frame.setPosition(newX, newY);
            frame.setSize(newW, newH);
        } else {
            // 如果内部被清空了，维持最后的状态或重置为默认大小，这里选择不强制缩回
            // frame.setSize(200f, 200f);
        }

        // 4. 通知 UI 层重绘该框
        for (EditorContext.EditorListener l : mContext.getListeners()) {
            l.onFrameBoundsUpdated(frameId, frame.uiPos[0], frame.uiPos[1], frame.uiSize[0], frame.uiSize[1]);
        }

        // 5. 递归：如果当前图框本身也被包在另一个图框里，大图框也要跟着变大
        if (frame.parentFrame != null) {
            updateFrameBounds(frame.parentFrame);
        }
    }

    public void setFrameProperty(String frameId, String title, int color) {
        com.mine.geometry_node.core.node.FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame != null) {
            frame.title = title;
            frame.color = color;

            // 通知 UI 层
            for (EditorContext.EditorListener l : mContext.getListeners()) {
                l.onFrameTitleChanged(frameId, title); // 也可以顺便把改色逻辑放进去，UIFrame接到后重绘即可
            }
        }
    }

    /**
     * 设置图框的位置（主要用于移动空图框）
     */
    public void setFramePosition(String frameId, float x, float y) {
        com.mine.geometry_node.core.node.FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame != null) {
            frame.setPosition(x, y);

            // 通知 UI 层该框位置已改变
            for (EditorContext.EditorListener l : mContext.getListeners()) {
                l.onFrameBoundsUpdated(frameId, frame.uiPos[0], frame.uiPos[1], frame.uiSize[0], frame.uiSize[1]);
            }

            // 联动：如果这个空框自己是被包在一个大图框里的，它移动了，外层大图框也得跟着重算
            if (frame.parentFrame != null) {
                updateFrameBounds(frame.parentFrame);
            }
        }
    }
}