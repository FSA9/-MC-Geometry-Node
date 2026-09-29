package com.mine.geometry_node.core.engine.system.model.runtime;
import com.mine.geometry_node.core.engine.system.model.asset.ModelScene;
import org.joml.*;
/** Instance-owned mutable evaluation state. Snapshot never aliases instance or asset matrices. */
public final class ModelPose {
    private final ModelScene scene;
    private final Matrix4f[] local;
    public ModelPose(ModelScene scene) {
        this.scene=scene; local=new Matrix4f[scene.nodes().size()]; reset();
    }
    public void reset() { for(int i=0;i<local.length;i++) local[i]=scene.nodes().get(i).local(); }
    public void setLocal(int node, Matrix4fc transform) {
        Matrix4f copy=new Matrix4f(transform);
        if (!copy.isFinite()) throw new IllegalArgumentException("non-finite pose");
        local[node]=copy;
    }
    public Snapshot snapshot() {
        Matrix4f[] world=new Matrix4f[local.length];
        for(int i=0;i<world.length;i++) {
            int parent=scene.nodes().get(i).parent();
            world[i]=parent<0?new Matrix4f(local[i]):new Matrix4f(world[parent]).mul(local[i]);
        }
        return new Snapshot(world);
    }
    public static final class Snapshot {
        private final Matrix4f[] world;
        private Snapshot(Matrix4f[] world) { this.world=world; }
        public int size() { return world.length; }
        public Matrix4f world(int node) { return new Matrix4f(world[node]); }
    }
}
