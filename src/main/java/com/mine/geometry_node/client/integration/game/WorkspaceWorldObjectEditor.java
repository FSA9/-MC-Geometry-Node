package com.mine.geometry_node.client.integration.game;

import com.mine.geometry_node.client.feature.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.ui.components.integration.worldobject.editor.WorldObjectEditorDialog;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import icyllis.modernui.view.View;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Workspace composition keeps native scene metadata subscribed while a world-object modal needs it. */
public final class WorkspaceWorldObjectEditor {
    private static final AtomicInteger nativeEditors = new AtomicInteger();
    private WorkspaceWorldObjectEditor() {}
    public static boolean requestsNativeObjects() { return nativeEditors.get() > 0; }
    public static void show(View owner, WorldObjectEntry entry, ClientWorldObjectRepository.Session source,
                            BooleanSupplier scopeCurrent, Runnable applied) {
        boolean nativeObject = entry.provider().equals("native");
        if (nativeObject) nativeEditors.incrementAndGet();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (nativeObject && released.compareAndSet(false, true)) nativeEditors.decrementAndGet();
        };
        try { WorldObjectEditorDialog.show(owner, entry, source, scopeCurrent, applied, release); }
        catch (RuntimeException failure) { release.run(); throw failure; }
    }
}
