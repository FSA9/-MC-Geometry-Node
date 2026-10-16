package com.mine.geometry_node.client.ui.workspace.area;

import com.mine.geometry_node.client.ui.workspace.persistence.EditorSessionState;
import icyllis.modernui.core.Context;

/** Creates editor content for a leaf using its existing persisted state. */
@FunctionalInterface
public interface AreaEditorFactory {
    AreaEditorWindow create(Context context, EditorSessionState.AreaState sessionState,
                            Runnable sessionChanged);
}
