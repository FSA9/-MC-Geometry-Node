package com.mine.geometry_node.core.engine.system.model.gltf;

import com.mine.geometry_node.core.engine.system.model.io.ModelCancellation;

import com.mine.geometry_node.core.engine.system.model.asset.GltfAsset;
import java.io.IOException;
import java.util.Arrays;

/** Angle-weighted UV tangent basis. Preserves authored seams and splits shared mirrored corners.
 * This is deliberately not advertised as a bit-exact MikkTSpace implementation.
 */
final class GltfTangents {
    record Result(float[] positions, float[] normals, float[] uv0, float[] uv1, float[] colors,
                  float[] tangents, int[] indices) {}

    static boolean needsGeneration(GltfAsset.TextureInfo normal, float[] tangents) {
        if (normal == null) return false;
        float[] scale=normal.scale();
        return tangents.length==0 || normal.texCoord()!=0 || normal.rotation()!=0 || scale[0]!=1 || scale[1]!=1;
    }

    static Result generate(float[] p,float[] n,float[] uv0,float[] uv1,float[] colors,int[] indices,
                           GltfAsset.TextureInfo normal, boolean replacesTangents, GltfImportBudget budget,
                           String path, ModelCancellation token) throws IOException {
        int count=p.length/3;
        // Bound scratch as well as retained arrays before any allocation.
        budget.checkTemporary((long)count*48, path+"/tangents");
        byte[] orientation=new byte[count];
        int[] duplicate=new int[count]; Arrays.fill(duplicate,-1);
        float[] uv=normal.texCoord()==1?uv1:uv0;
        float[] scale=normal.scale();
        double cos=Math.cos(normal.rotation()),sin=Math.sin(normal.rotation());
        int extra=0;
        for(int t=0;t<indices.length;t+=3) {
            if((t&16383)==0)token.check();
            int a=indices[t],b=indices[t+1],c=indices[t+2];
            double det=determinant(uv,a,b,c)*scale[0]*scale[1];
            byte sign=(byte)(det<0?-1:1);
            for(int corner=0;corner<3;corner++) {
                int index=indices[t+corner];
                if(orientation[index]==0)orientation[index]=sign;
                else if(orientation[index]!=sign && duplicate[index]<0)duplicate[index]=count+extra++;
            }
        }
        int vertices=Math.addExact(count,extra);
        long stride=6+(uv0.length==0?0:2)+(uv1.length==0?0:2)+(colors.length==0?0:4);
        budget.reserve((long)extra*stride*4+(long)(replacesTangents?extra:vertices)*16,path+"/tangents");
        budget.checkTemporary((long)vertices*24+(long)count*5+(extra>0?(long)count*stride*4:0)
                +(replacesTangents?(long)count*16:0),path+"/tangents scratch");
        float[] outP=copySplit(p,3,vertices,duplicate),outN=copySplit(n,3,vertices,duplicate);
        float[] outU0=copySplit(uv0,2,vertices,duplicate),outU1=copySplit(uv1,2,vertices,duplicate),outC=copySplit(colors,4,vertices,duplicate);
        float[] sumT=new float[vertices*3],sumB=new float[vertices*3];
        for(int t=0;t<indices.length;t+=3) {
            if((t&16383)==0)token.check();
            int a=indices[t],b=indices[t+1],c=indices[t+2];
            double du1=((double)uv[b*2]-uv[a*2])*scale[0],dv1=((double)uv[b*2+1]-uv[a*2+1])*scale[1];
            double du2=((double)uv[c*2]-uv[a*2])*scale[0],dv2=((double)uv[c*2+1]-uv[a*2+1])*scale[1];
            double u1=cos*du1-sin*dv1,v1=sin*du1+cos*dv1,u2=cos*du2-sin*dv2,v2=sin*du2+cos*dv2;
            double det=u1*v2-u2*v1;
            byte sign=(byte)(determinant(uv,a,b,c)*scale[0]*scale[1]<0?-1:1);
            for(int corner=0;corner<3;corner++) {
                int index=indices[t+corner];
                if(orientation[index]!=sign)indices[t+corner]=duplicate[index];
            }
            if(Math.abs(det)<1e-20)continue;
            double ex=p[b*3]-p[a*3],ey=p[b*3+1]-p[a*3+1],ez=p[b*3+2]-p[a*3+2];
            double fx=p[c*3]-p[a*3],fy=p[c*3+1]-p[a*3+1],fz=p[c*3+2]-p[a*3+2];
            double tx=(ex*v2-fx*v1)/det,ty=(ey*v2-fy*v1)/det,tz=(ez*v2-fz*v1)/det;
            double bx=(fx*u1-ex*u2)/det,by=(fy*u1-ey*u2)/det,bz=(fz*u1-ez*u2)/det;
            double tl=Math.sqrt(tx*tx+ty*ty+tz*tz),bl=Math.sqrt(bx*bx+by*by+bz*bz);
            if(tl<1e-20||bl<1e-20)continue;
            for(int corner=0;corner<3;corner++) {
                int vertex=indices[t+corner],j=indices[t+(corner+1)%3],k=indices[t+(corner+2)%3];
                double weight=angle(outP,vertex,j,k); int o=vertex*3;
                sumT[o]+=(float)(tx/tl*weight);sumT[o+1]+=(float)(ty/tl*weight);sumT[o+2]+=(float)(tz/tl*weight);
                sumB[o]+=(float)(bx/bl*weight);sumB[o+1]+=(float)(by/bl*weight);sumB[o+2]+=(float)(bz/bl*weight);
            }
        }
        float[] tangents=new float[vertices*4];
        for(int v=0;v<vertices;v++) {
            if((v&16383)==0)token.check();
            int o=v*3;double nx=outN[o],ny=outN[o+1],nz=outN[o+2],nl=Math.sqrt(nx*nx+ny*ny+nz*nz);
            if(nl<1e-20){nx=0;ny=1;nz=0;}else{nx/=nl;ny/=nl;nz/=nl;}
            double dot=nx*sumT[o]+ny*sumT[o+1]+nz*sumT[o+2];
            double tx=sumT[o]-nx*dot,ty=sumT[o+1]-ny*dot,tz=sumT[o+2]-nz*dot;
            double len=Math.sqrt(tx*tx+ty*ty+tz*tz);
            if(len<1e-20){ if(Math.abs(nx)<.9){tx=1-nx*nx;ty=-nx*ny;tz=-nx*nz;}else{tx=-ny*nx;ty=1-ny*ny;tz=-ny*nz;}len=Math.sqrt(tx*tx+ty*ty+tz*tz); }
            tx/=len;ty/=len;tz/=len;
            tangents[v*4]=(float)tx;tangents[v*4+1]=(float)ty;tangents[v*4+2]=(float)tz;
            double handed=(ny*tz-nz*ty)*sumB[o]+(nz*tx-nx*tz)*sumB[o+1]+(nx*ty-ny*tx)*sumB[o+2];
            tangents[v*4+3]=handed<0?-1:1;
        }
        return new Result(outP,outN,outU0,outU1,outC,tangents,indices);
    }
    private static double determinant(float[] uv,int a,int b,int c) {
        return ((double)uv[b*2]-uv[a*2])*((double)uv[c*2+1]-uv[a*2+1])-((double)uv[c*2]-uv[a*2])*((double)uv[b*2+1]-uv[a*2+1]);
    }
    private static double angle(float[] p,int a,int b,int c) {
        double x=p[b*3]-p[a*3],y=p[b*3+1]-p[a*3+1],z=p[b*3+2]-p[a*3+2];
        double u=p[c*3]-p[a*3],v=p[c*3+1]-p[a*3+1],w=p[c*3+2]-p[a*3+2];
        double den=Math.sqrt((x*x+y*y+z*z)*(u*u+v*v+w*w));
        return den<1e-20?0:Math.acos(Math.max(-1,Math.min(1,(x*u+y*v+z*w)/den)));
    }
    private static float[] copySplit(float[] source,int width,int vertices,int[] duplicate) {
        if(source.length==0||source.length==vertices*width)return source;
        float[] result=Arrays.copyOf(source,vertices*width);
        for(int v=0;v<duplicate.length;v++)if(duplicate[v]>=0)System.arraycopy(source,v*width,result,duplicate[v]*width,width);
        return result;
    }
}
