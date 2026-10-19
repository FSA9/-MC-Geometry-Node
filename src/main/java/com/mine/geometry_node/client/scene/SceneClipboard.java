package com.mine.geometry_node.client.scene;

import com.mine.geometry_node.core.engine.system.scene.SceneObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Detached scene values, shared by this client's viewports; never shares graph or OS clipboard data. */
record SceneClipboard(List<SceneObject> objects, UUID active) {
    static final SceneClipboard EMPTY = new SceneClipboard(List.of(), null);

    SceneClipboard {
        objects = List.copyOf(objects);
        if (active != null && objects.stream().noneMatch(object -> object.id().equals(active)))
            throw new IllegalArgumentException("Clipboard active object is not a member");
    }

    /** Only identity and server revision change. All type-owned parameters and common TRS survive. */
    SceneClipboard duplicate() {
        List<SceneObject> copies = new ArrayList<>(objects.size());
        UUID copiedActive = null;
        for (SceneObject object : objects) {
            UUID id = UUID.randomUUID();
            copies.add(new SceneObject(id, object.typeId(), object.name(), object.position(), object.color(),
                    object.enabled(), 0, object.parameters(), object.rotation(), object.scale(), object.metadata()));
            if (object.id().equals(active)) copiedActive = id;
        }
        return new SceneClipboard(copies, copiedActive);
    }
}
