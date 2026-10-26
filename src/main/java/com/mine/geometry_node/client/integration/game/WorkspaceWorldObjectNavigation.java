package com.mine.geometry_node.client.integration.game;

import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.ui.editor.game.GameViewportWindow;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceRegistry;
import com.mine.geometry_node.client.ui.workspace.surface.UiSurfaceType;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import net.minecraft.world.phys.AABB;
import java.util.*;
import net.minecraft.network.chat.Component;

/** Resolves a live viewport at action time, so splitting/closing Areas cannot leave stale targets. */
public final class WorkspaceWorldObjectNavigation {
    private WorkspaceWorldObjectNavigation() {}
    private static String tr(String key, Object... args) {
        return Component.translatable("geometry_node.database." + key, args).getString();
    }

    public static String focus(List<WorldObjectEntry> entries) {
        if (entries.isEmpty()) return tr("select_hint");
        String dimension = entries.getFirst().dimension();
        if (entries.stream().anyMatch(entry -> !entry.dimension().equals(dimension))) return tr("focus_same_dimension");
        if (!ClientSceneService.INSTANCE.snapshot().dimension().equals(dimension)) return tr("focus_other_dimension", dimension);
        AABB bounds = entries.getFirst().bounds();
        Set<UUID> ids = new LinkedHashSet<>();
        for (WorldObjectEntry entry : entries) {
            bounds = bounds.minmax(entry.bounds());
            if (entry.sceneObjectId() != null) ids.add(entry.sceneObjectId());
        }
        var leases = new ArrayList<>(UiSurfaceRegistry.INSTANCE.leases(UiSurfaceType.GAME_VIEWPORT, GameViewportWindow.class));
        leases.sort(Comparator.comparingLong((UiSurfaceRegistry.Lease<GameViewportWindow> lease) -> lease.interactionSerial()).reversed());
        for (var lease : leases) {
            if (lease.visible() && lease.isCurrent() && lease.owner().focusWorldObjects(dimension, bounds, ids))
                return tr("focused", entries.size());
        }
        return tr("focus_no_viewport");
    }
}
