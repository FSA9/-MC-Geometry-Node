package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import java.util.EnumSet;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Per-view presentation, never changes the actual scene object's enabled state. */
public final class ViewportVisualization {
    public enum Layer { LIGHT, GEOMETRY, MODEL, SCHEMATIC, AREA, COLLISION, MODEL_COLLISION, INTERACTION, PATHFINDING }
    public record Snapshot(Set<Layer> visible, Set<Layer> selectable, boolean helpers, boolean gizmo, boolean grid) {
        public Snapshot(Set<Layer> visible, Set<Layer> selectable, boolean helpers, boolean gizmo) {
            this(visible, selectable, helpers, gizmo, false);
        }
        public Snapshot { visible = Set.copyOf(visible); selectable = Set.copyOf(selectable); }
        public boolean visible(Layer layer) { return visible.contains(layer); }
        public boolean selectable(Layer layer) { return visible(layer) && selectable.contains(layer); }
    }
    private static final Snapshot DEFAULT = new Snapshot(EnumSet.of(Layer.LIGHT, Layer.GEOMETRY, Layer.MODEL, Layer.SCHEMATIC, Layer.AREA, Layer.COLLISION),
            EnumSet.of(Layer.LIGHT, Layer.GEOMETRY, Layer.AREA, Layer.COLLISION), true, true);
    private static final Map<Object, Snapshot> VIEWS = new ConcurrentHashMap<>();
    private final EnumSet<Layer> visible = EnumSet.copyOf(DEFAULT.visible());
    private final EnumSet<Layer> selectable = EnumSet.copyOf(DEFAULT.selectable());
    private boolean helpers = true, gizmo = true, grid;
    public synchronized boolean visible(Layer layer) { return visible.contains(layer); }
    public synchronized boolean selectable(Layer layer) { return visible(layer) && selectable.contains(layer); }
    public synchronized void setVisible(Layer layer, boolean value) { if (value) visible.add(layer); else visible.remove(layer); }
    public synchronized void setSelectable(Layer layer, boolean value) { if (value) selectable.add(layer); else selectable.remove(layer); }
    public synchronized boolean helpers() { return helpers; }
    public synchronized void setHelpers(boolean value) { helpers = value; }
    public synchronized boolean gizmo() { return gizmo; }
    public synchronized void setGizmo(boolean value) { gizmo = value; }
    public synchronized boolean grid() { return grid; }
    public synchronized void setGrid(boolean value) { grid = value; }
    public synchronized Snapshot snapshot() { return new Snapshot(visible, selectable, helpers, gizmo, grid); }
    public static void publish(Object owner, Snapshot snapshot) { if (owner != null) VIEWS.put(owner, snapshot); }
    public static void remove(Object owner) { if (owner != null) VIEWS.remove(owner); }
    public static Snapshot forOwner(Object owner) { return owner == null ? DEFAULT : VIEWS.getOrDefault(owner, DEFAULT); }
    public static Snapshot current() { return forOwner(EditViewportRuntime.renderingOwner()); }
    public static boolean inEditorPass() { return EditViewportRuntime.renderingOwner() != null; }
    public static int requestedDebugMask() {
        int mask = 0;
        for (Snapshot view : VIEWS.values()) if (view.helpers())
            for (var layer : com.mine.geometry_node.core.engine.graph.debug.ViewportDebugLayer.values())
                if (view.visible(Layer.valueOf(layer.name()))) mask |= layer.bit();
        return mask;
    }
}
