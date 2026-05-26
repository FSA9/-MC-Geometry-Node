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
import java.util.Map;
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

        // 6. 清理失效的端口自定义配置
        ensurePortSettings(node);
        removeInvalidPortSettings(node.portSettings.inputs, validInputs);
        removeInvalidPortSettings(node.portSettings.execInputs, validInputs);
        removeInvalidPortSettings(node.portSettings.outputs, validOutputs);
        removeInvalidPortSettings(node.portSettings.execOutputs, validOutputs);

        // 7. 清理其他节点连接到当前节点失效【输入】端口的连线
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

        // 8. 通知 viewport 重新构建该节点的 UI
        mContext.notifyNodeStructureChanged(node);
    }

    public boolean isInputPortConnected(String targetNodeId, String targetPortId) {
        NodeData node = mContext.getGraph().getNode(targetNodeId);
        if (node == null) return false;
        return node.connectedInputs.contains(targetPortId);
    }

    public void removeDynamicBranch(String nodeId, String propertyKey, int removeIndex, int totalCount) {
        NodeData node = mContext.getGraph().getNode(nodeId);
        if (node == null) return;

        if (removeIndex < 1 || removeIndex > totalCount) return;
        ensurePortSettings(node);

        for (int i = removeIndex; i < totalCount; i++) {
            String oldSuffix = "_" + (i + 1);
            String newSuffix = "_" + i;
            shiftMapData(node.inputs, oldSuffix, newSuffix);
            shiftMapData(node.outputs, oldSuffix, newSuffix);
            shiftMapData(node.execOutputs, oldSuffix, newSuffix);
            shiftPortSettings(node.portSettings, oldSuffix, newSuffix);
            shiftConnections(nodeId, oldSuffix, newSuffix);
        }

        String lastSuffix = "_" + totalCount;
        node.inputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.outputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        node.execOutputs.keySet().removeIf(k -> k.endsWith(lastSuffix));
        removePortSettingsSuffix(node.portSettings, lastSuffix);
        shiftConnections(nodeId, lastSuffix, null);

        setNodeInputValue(nodeId, propertyKey, totalCount - 1);
    }

    private <V> void shiftMapData(java.util.Map<String, V> map, String oldSuffix, String newSuffix) {
        map.keySet().removeIf(k -> k.endsWith(newSuffix));

        Map<String, V> toMove = new java.util.HashMap<>();
        for (Map.Entry<String, V> entry : map.entrySet()) {
            if (entry.getKey().endsWith(oldSuffix)) {
                String newKey = entry.getKey().substring(0, entry.getKey().length() - oldSuffix.length()) + newSuffix;
                toMove.put(newKey, entry.getValue());
            }
        }

        map.keySet().removeIf(k -> k.endsWith(oldSuffix));
        map.putAll(toMove);
    }

    private void shiftPortSettings(NodeData.PortSettings settings, String oldSuffix, String newSuffix) {
        if (settings == null) return;
        shiftMapData(settings.inputs, oldSuffix, newSuffix);
        shiftMapData(settings.execInputs, oldSuffix, newSuffix);
        shiftMapData(settings.outputs, oldSuffix, newSuffix);
        shiftMapData(settings.execOutputs, oldSuffix, newSuffix);
    }

    private void removePortSettingsSuffix(NodeData.PortSettings settings, String suffix) {
        if (settings == null) return;
        settings.inputs.keySet().removeIf(k -> k.endsWith(suffix));
        settings.execInputs.keySet().removeIf(k -> k.endsWith(suffix));
        settings.outputs.keySet().removeIf(k -> k.endsWith(suffix));
        settings.execOutputs.keySet().removeIf(k -> k.endsWith(suffix));
    }

    private void removeInvalidPortSettings(Map<String, NodeData.PortConfig> settings, Set<String> validPorts) {
        if (settings == null) return;
        settings.keySet().removeIf(portId -> !validPorts.contains(portId));
    }

    private void ensurePortSettings(NodeData node) {
        if (node.portSettings == null) {
            node.portSettings = new NodeData.PortSettings();
        }
        if (node.portSettings.inputs == null) node.portSettings.inputs = new java.util.HashMap<>();
        if (node.portSettings.execInputs == null) node.portSettings.execInputs = new java.util.HashMap<>();
        if (node.portSettings.execOutputs == null) node.portSettings.execOutputs = new java.util.HashMap<>();
        if (node.portSettings.outputs == null) node.portSettings.outputs = new java.util.HashMap<>();
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
        ensurePortSettings(node);

        // 根据 category 获取对应的 Map
        Map<String, NodeData.PortConfig> targetMap = switch (category) {
            case "inputs" -> node.portSettings.inputs;
            case "exec_inputs" -> node.portSettings.execInputs;
            case "exec_outputs" -> node.portSettings.execOutputs;
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
     * Updates an element's parent frame and refreshes affected frame bounds.
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
     * Recomputes a frame's auto bounds from committed graph data.
     */
    public void updateFrameBounds(String frameId) {
        com.mine.geometry_node.core.node.FrameData frame = mContext.getGraph().getFrame(frameId);
        if (frame == null) return;

        FrameBoundsCalculator.Result bounds = FrameBoundsCalculator.computeCommittedBounds(
                frameId,
                mContext.getGraph().nodes.values(),
                mContext.getGraph().frames.values(),
                null
        );

        // 3. 应用计算结果
        if (bounds.hasChildren()) {
            frame.setPosition(bounds.x(), bounds.y());
            frame.setSize(bounds.width(), bounds.height());
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
