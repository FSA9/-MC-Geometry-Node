package com.mine.geometry_node.client.ui.viewport.interaction;

import com.mine.geometry_node.client.ui.UICommand.commands.CmdPasteNodes;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveNodes;
import com.mine.geometry_node.client.ui.viewport.UINode;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.NodeGraph;
import icyllis.modernui.view.KeyEvent;

import java.util.ArrayList;
import java.util.List;

public class KeyManager {

    // GLFW 常量
    private static final int GLFW_KEY_DELETE = 261;

    // 内存剪贴板（以 JSON 字符串形式存在）
    private static String sClipboardJson = null;

    private final InteractionContext mContext;

    public KeyManager(InteractionContext context) {
        this.mContext = context;
    }

    public boolean onKeyDown(KeyEvent event) {
        boolean isCtrl = event.isCtrlPressed();
        int keyCode = event.getKeyCode();

        if (isCtrl) {
            switch (keyCode) {
                case KeyEvent.KEY_Z: // Ctrl+Z 撤销
                    mContext.getEditorContext().getCommandManager().undo();
                    return true;
                case KeyEvent.KEY_Y: // Ctrl+Y 重做
                    mContext.getEditorContext().getCommandManager().redo();
                    return true;
                case KeyEvent.KEY_S: // Ctrl+S 保存
                    performSaveJSON();
                    return true;
                case KeyEvent.KEY_C: // Ctrl+C 复制
                    performCopy();
                    return true;
                case KeyEvent.KEY_V: // Ctrl+V 粘贴
                    performPaste();
                    return true;
            }
        } else {
            if (keyCode == GLFW_KEY_DELETE) { // Delete 删除
                performDelete();
                return true;
            }
        }
        return false;
    }

    /**
     * 执行复制 (Ctrl + C)
     */
    private void performCopy() {
        List<UINode> selected = mContext.getSelectedNodes();
        if (selected.isEmpty()) return;

        // 1. 创建一个临时的子图，把选中的节点放进去
        NodeGraph tempGraph = new NodeGraph("Clipboard");
        for (UINode uiNode : selected) {
            NodeData data = uiNode.getNodeData();
            tempGraph.nodes.put(data.id, data);
        }

        // 2. 利用你现成的 GraphJsonIO 把它变成 JSON 字符串，存入剪贴板
        sClipboardJson = GraphJsonIO.toJson(tempGraph);
        System.out.println("Copied " + selected.size() + " nodes to clipboard.");
    }

    /**
     * 执行粘贴 (Ctrl + V)
     */
    private void performPaste() {
        if (sClipboardJson == null || sClipboardJson.isEmpty()) return;

        // 从 Context 中拉取鼠标最后的逻辑坐标 (需要你将 InteractionContext 转型为 viewport 或者补充接口)
        float uiX = ((com.mine.geometry_node.client.ui.viewport.Viewport)mContext).getLastMouseUiX();
        float uiY = ((com.mine.geometry_node.client.ui.viewport.Viewport)mContext).getLastMouseUiY();

        CmdPasteNodes cmd = new CmdPasteNodes(
                mContext.getEditorContext().getGraphController(),
                sClipboardJson,
                uiX, uiY  // 传入真实鼠标坐标
        );
        mContext.getEditorContext().getCommandManager().execute(cmd);

        System.out.println("Pasted nodes from clipboard.");
    }

    /**
     * 执行删除 (Delete)
     */
    private void performDelete() {
        List<UINode> selected = mContext.getSelectedNodes();
        if (selected.isEmpty()) return;

        // 收集要删除的 ID
        List<String> idsToRemove = new ArrayList<>();
        for (UINode uiNode : selected) {
            idsToRemove.add(uiNode.getNodeData().id);
        }

        // 执行批量删除命令
        CmdRemoveNodes cmd = new CmdRemoveNodes(
                mContext.getEditorContext().getGraphController(),
                mContext.getEditorContext().getGraph(),
                idsToRemove
        );
        mContext.getEditorContext().getCommandManager().execute(cmd);

        // 删除后清空选中状态
        mContext.clearSelection();
    }

    private void performSaveJSON() {
        mContext.requestViewportFocus();

        DocumentManager.INSTANCE.saveSession(DocumentManager.INSTANCE.getActiveSession());
    }
}