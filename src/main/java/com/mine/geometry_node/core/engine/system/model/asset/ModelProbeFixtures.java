package com.mine.geometry_node.core.engine.system.model.asset;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
/** Deterministic orientation fixture: colored faces, normal/mirror/non-uniform transform cases. */
public final class ModelProbeFixtures {
    private ModelProbeFixtures() {}
    public static ModelMesh cube(Matrix4f transform) {
        float[][] corners={
            {-1,-1,1, 1,-1,1, 1,1,1, -1,1,1},
            {1,-1,-1, -1,-1,-1, -1,1,-1, 1,1,-1},
            {1,-1,1, 1,-1,-1, 1,1,-1, 1,1,1},
            {-1,-1,-1, -1,-1,1, -1,1,1, -1,1,-1},
            {-1,1,1, 1,1,1, 1,1,-1, -1,1,-1},
            {-1,-1,-1, 1,-1,-1, 1,-1,1, -1,-1,1}};
        float[][] normals={{0,0,1},{0,0,-1},{1,0,0},{-1,0,0},{0,1,0},{0,-1,0}};
        int[] palette={0xffff6060,0xffa04040,0xff60ff60,0xff40a040,0xff6080ff,0xff4040a0};
        float[] vertices=new float[24*8]; int[] colors=new int[24],indices=new int[36];
        Matrix3f nm=transform.normal(new Matrix3f()); boolean mirrored=transform.determinant3x3()<0;
        for(int face=0;face<6;face++) {
            Vector3f normal=nm.transform(new Vector3f(normals[face])).normalize();
            for(int corner=0;corner<4;corner++) {
                int v=face*4+corner,at=v*8;
                Vector3f p=transform.transformPosition(new Vector3f(corners[face][corner*3]*.5f,
                        corners[face][corner*3+1]*.5f,corners[face][corner*3+2]*.5f));
                vertices[at]=p.x; vertices[at+1]=p.y; vertices[at+2]=p.z;
                vertices[at+3]=normal.x; vertices[at+4]=normal.y; vertices[at+5]=normal.z;
                vertices[at+6]=corner==1||corner==2?1:0; vertices[at+7]=corner>=2?0:1; colors[v]=palette[face];
            }
            int[] triangle={0,1,2,0,2,3};
            for(int k=0;k<6;k++) indices[face*6+k]=face*4+triangle[mirrored?(k/3*3+(k%3==0?0:3-k%3)):k];
        }
        return new ModelMesh(vertices,colors,indices);
    }
}
