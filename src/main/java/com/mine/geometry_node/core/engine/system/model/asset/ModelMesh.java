package com.mine.geometry_node.core.engine.system.model.asset;
import java.util.Objects;
/** Immutable indexed triangle geometry: position/normal/uv (8 floats), ARGB per vertex. */
public final class ModelMesh {
    private final float[] attributes;
    private final int[] colors,indices;
    public ModelMesh(float[] attributes,int[] colors,int[] indices) {
        Objects.requireNonNull(attributes); Objects.requireNonNull(colors); Objects.requireNonNull(indices);
        if(attributes.length==0 || attributes.length%8!=0 || colors.length!=attributes.length/8 || indices.length%3!=0)
            throw new IllegalArgumentException("inconsistent mesh attributes");
        for(float f:attributes) if(!Float.isFinite(f)) throw new IllegalArgumentException("non-finite vertex");
        for(int i:indices) if(i<0||i>=colors.length) throw new IllegalArgumentException("index out of range");
        this.attributes=attributes.clone(); this.colors=colors.clone(); this.indices=indices.clone();
    }
    public int vertexCount() { return colors.length; }
    public int indexCount() { return indices.length; }
    public float attribute(int vertex,int component) { return attributes[vertex*8+component]; }
    public int color(int vertex) { return colors[vertex]; }
    public int index(int index) { return indices[index]; }
    public long cpuBytes() { return (long)attributes.length*4+(long)(colors.length+indices.length)*4; }
}
