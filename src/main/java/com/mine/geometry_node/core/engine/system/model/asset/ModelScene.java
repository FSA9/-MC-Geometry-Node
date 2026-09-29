package com.mine.geometry_node.core.engine.system.model.asset;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
/** Minimal immutable scene contract. Names are labels; node indices are identities. */
public final class ModelScene {
    public static final class Node {
        private final String name;
        private final int parent;
        private final Matrix4f local;
        public Node(String name, int parent, Matrix4fc local) {
            this.name = Objects.requireNonNull(name); this.parent = parent; this.local = new Matrix4f(local);
            if (!this.local.isFinite()) throw new IllegalArgumentException("non-finite node matrix");
        }
        public String name() { return name; }
        public int parent() { return parent; }
        public Matrix4f local() { return new Matrix4f(local); }
    }
    private final List<Node> nodes;
    public ModelScene(List<Node> nodes) {
        this.nodes = List.copyOf(nodes);
        // Topological order is the compiler output contract; importer remaps source indices once.
        for (int i=0;i<this.nodes.size();i++)
            if (this.nodes.get(i).parent() < -1 || this.nodes.get(i).parent() >= i)
                throw new IllegalArgumentException("parent must precede child; cyclic/invalid scene at " + i);
    }
    public List<Node> nodes() { return nodes; }
}
