package com.mine.geometry_node.client.ui.Viewport.Interaction;

import com.mine.geometry_node.client.ui.UICommand.commands.CmdRemoveNode;
import com.mine.geometry_node.client.ui.Viewport.UINode;
import com.mine.geometry_node.client.ui.persistence.GraphJsonIO;
import icyllis.modernui.view.KeyEvent;

public class KeyManager {

    private static final int GLFW_KEY_DELETE = 261;

    private final InteractionContext mContext;

    public KeyManager(InteractionContext context) {
        this.mContext = context;
    }

    public boolean onKeyDown(KeyEvent event) {
        boolean isCtrl = event.isCtrlPressed();

        switch (event.getKeyCode()) {
            case KeyEvent.KEY_Z:
                if (isCtrl) {
                    mContext.getEditorContext().getCommandManager().undo();
                    return true;
                }
                break;
            case KeyEvent.KEY_Y:
                if (isCtrl) {
                    mContext.getEditorContext().getCommandManager().redo();
                    return true;
                }
                break;
            case KeyEvent.KEY_S:
                if (isCtrl) {
                    performSaveJSON();
                    return true;
                }
                break;
//            case GLFW_KEY_DELETE:
//                if (!isCtrl) {
//                    for (UINode node : mContext.getSelectedNodes()) {
//                        String id = node.getNodeData().id;
//                        mContext.getEditorContext().getCommandManager().execute(
//                                new CmdRemoveNode(mContext.getEditorContext().getGraphController(), id));
//                    }
//                    mContext.clearSelection();
//                    return true;
//                }
//                break;
        }
        return false;
    }

    private void performSaveJSON() {
        // 强制视口请求焦点，夺走正在编辑的 EditText 的焦点，触发结算
        mContext.requestViewportFocus();

        String jsonOutput = GraphJsonIO.toJson(mContext.getEditorContext().getGraph());
        System.out.println(jsonOutput);
    }
}