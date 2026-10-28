package com.mine.geometry_node.client.integration.game;

import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.feature.worldquery.ClientWorldObjectRepository;
import com.mine.geometry_node.client.integration.entity.WorkspaceEntityEditor;
import com.mine.geometry_node.client.runtime.game.selection.ViewportEntitySource;
import com.mine.geometry_node.client.runtime.game.selection.ViewportTarget;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeEntities;
import com.mine.geometry_node.core.engine.system.scene.SceneNativeInteractions;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectLookup;
import icyllis.modernui.mc.MuiModApi;
import icyllis.modernui.view.View;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Routes a viewport identity to the existing editors; owns only the one-shot metadata lookup. */
public final class WorkspaceSceneObjectEditor {
    private static final String SCENE = "geometry_node:scene";
    private WorkspaceSceneObjectEditor() {}

    private record Target(UUID id, SceneObject object, boolean entity) {}

    public static boolean supports(ClientSceneService.Snapshot snapshot, Set<ViewportTarget.Key> selection) {
        return target(snapshot, selection) != null;
    }

    private static Target target(ClientSceneService.Snapshot snapshot, Set<ViewportTarget.Key> selection) {
        if (!snapshot.canEdit() || snapshot.pending() || snapshot.dimension().isEmpty() || selection.size() != 1) return null;
        var key = selection.iterator().next();
        if (!key.source().equals(SCENE) && !key.source().equals(ViewportEntitySource.ID)) return null;
        UUID id;
        try { id = UUID.fromString(key.id()); }
        catch (IllegalArgumentException invalid) { return null; }
        SceneObject object = snapshot.object(id);
        if (key.source().equals(SCENE) && object == null) return null;
        return new Target(id, object, key.source().equals(ViewportEntitySource.ID)
                || object != null && SceneNativeEntities.TYPE_ID.equals(object.typeId()));
    }

    public static void show(View owner, ClientSceneService.Snapshot snapshot, Set<ViewportTarget.Key> selection,
                            BooleanSupplier openingCurrent, BooleanSupplier scopeCurrent, Runnable applied) {
        Target target = target(snapshot, selection);
        if (target == null || !openingCurrent.getAsBoolean()) return;
        if (target.entity()) {
            SceneObject object = target.object();
            Vec3 position = object == null ? Vec3.ZERO : object.position();
            WorldObjectEntry entry = new WorldObjectEntry(WorldObjectEntry.key("native", "", target.id().toString()),
                    "native", object == null ? "" : object.parameters().getOrDefault(SceneNativeEntities.ENTITY_TYPE, ""),
                    "", snapshot.dimension(), position, new AABB(position, position), List.of(), "", "loaded",
                    target.id(), null, Map.of("entityUuid", target.id().toString()));
            WorkspaceEntityEditor.show(owner, entry, applied);
            return;
        }
        String provider = SceneNativeInteractions.TYPE_ID.equals(target.object().typeId()) ? "native" : "scene";
        var session = ClientWorldObjectRepository.INSTANCE.newSession();
        new Lookup(owner, session, new WorldObjectLookup(provider, snapshot.dimension(), target.id()),
                openingCurrent, scopeCurrent, applied).start();
    }

    private static final class Lookup {
        private final View owner;
        private final ClientWorldObjectRepository.Session session;
        private final WorldObjectLookup target;
        private final BooleanSupplier openingCurrent, scopeCurrent;
        private final Runnable applied;
        private final Runnable changed = () -> MuiModApi.postToUiThread(this::complete);
        private boolean finished;

        Lookup(View owner, ClientWorldObjectRepository.Session session, WorldObjectLookup target,
               BooleanSupplier openingCurrent, BooleanSupplier scopeCurrent, Runnable applied) {
            this.owner = owner; this.session = session; this.target = target;
            this.openingCurrent = openingCurrent; this.scopeCurrent = scopeCurrent; this.applied = applied;
        }
        void start() {
            session.addChangeListener(changed);
            try { session.lookup(target); }
            catch (RuntimeException failure) { finish(); throw failure; }
        }
        private void complete() {
            if (finished || session.snapshot().loading()) return;
            try {
                if (!openingCurrent.getAsBoolean()) return;
                var entry = session.snapshot().entries().stream().filter(value -> value.provider().equals(target.provider())
                        && value.dimension().equals(target.dimension()) && target.objectId().equals(value.sceneObjectId()))
                        .findFirst().orElse(null);
                if (entry != null) WorkspaceWorldObjectEditor.show(owner, entry, session, scopeCurrent, applied);
            } finally { finish(); }
        }
        private void finish() {
            if (finished) return;
            finished = true; session.removeChangeListener(changed); session.close();
        }
    }
}
