package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.client.runtime.light.diagnostic.LightReports;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightPrograms;
import com.mine.geometry_node.client.runtime.light.shader.NodeLightShader;
import com.mine.geometry_node.client.runtime.light.store.ClientLightStore;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.runtime.model.render.bvh.ModelPlacedLightField;
import com.mine.geometry_node.client.runtime.model.render.bvh.ModelSundialHeldLight;
import com.mine.geometry_node.client.runtime.model.render.diagnostic.ModelShaderGpuTimings;
import com.mine.geometry_node.client.runtime.model.render.geometry.ModelRenderAsset;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.*;

/** GL 3.3 light injection and parallel depth integration, independent of shaderpack shading. */
public final class NodeLightFroxels {
    private static final Map<Integer, Grid> GRIDS = new HashMap<>();
    private static final Map<Integer, Bindings> RECEIVERS = new HashMap<>();
    private static Program inject, scan;
    private static int vao;
    private static long injections, integrations, reuses;
    private static String status = "IDLE", failure = "", historyReason = "NO_HISTORY";
    private static boolean rendering;
    private static final String VERTEX = "#version 330 core\nvoid main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);gl_Position=vec4(p*2.0-1.0,0,1);}";

    private NodeLightFroxels() {}
    static int retainedGridCount() { return GRIDS.size(); }
    static int cachedReceiverCount() {
        return RECEIVERS.size() + (int) GRIDS.values().stream().filter(grid -> grid.history.valid).count();
    }
    static void clearCachedLookups() {
        RECEIVERS.clear();
        for (Grid grid : GRIDS.values()) {
            grid.history.valid = false;
            grid.history.lights = List.of();
            grid.history.camera = null;
        }
    }
    static boolean rendering() { return rendering; }

    static int draw(int receiver, int viewWidth, int viewHeight) {
        Bindings bindings = RECEIVERS.computeIfAbsent(receiver, Bindings::new);
        int ready = bindings.ready;
        if (ready < 0) return 0;
        GL20C.glUniform1i(ready, 0);
        if(viewWidth<=0 || viewHeight<=0)return 0;
        if (NodeLightRenderer.lights().stream().noneMatch(l -> enabled(l.definition().config().volume()))) return 0;
        Grid grid = GRIDS.computeIfAbsent(receiver, ignored -> new Grid());
        if(System.nanoTime()<grid.retryAfter)return 0;
        try {
            rendering = true;
            float[] inverseProjection = matrix(receiver, bindings.projection, bindings.projectionValue, "projection inverse");
            float[] inverseView = matrix(receiver, bindings.view, bindings.viewValue, "view inverse");
            boolean cameraTransmission = bindings.cameraTransmission;
            boolean stable = reusableScene();
            long worldRevision = stable ? ModelPlacedLightField.contentRevision() : 0;
            long entityRevision = stable && shadowedVolume() ? NodeLightEntityShadows.contentRevision() : 0;
            historyReason = stable ? "SCENE_OR_VIEW_CHANGED" : "DYNAMIC_OCCLUDERS_OR_MEDIUM";
            if (stable && grid.output != 0 && grid.history.matches(viewWidth, viewHeight, inverseProjection,
                    inverseView, cameraTransmission, worldRevision, entityRevision)) {
                reuses++; historyReason = "UNCHANGED_GRID_REUSED";
            } else {
                Matrix4f cameraToWorld = new Matrix4f().set(inverseView);
                Matrix4f projectionInverse = new Matrix4f().set(inverseProjection);
                Matrix4f viewProjection = new Matrix4f(projectionInverse).invert().mul(new Matrix4f(cameraToWorld).invert());
                Vector3f eye = cameraToWorld.getTranslation(new Vector3f());
                if (!layout(grid, viewWidth, viewHeight, eye, viewProjection)) {
                    grid.output = 0;
                    grid.history.valid = false;
                    status = "OFFSCREEN"; failure = ""; historyReason = "NO_VISIBLE_VOLUME";
                    return 0;
                }
                viewProjection.get(grid.viewProjection);
                grid.eye[0] = eye.x; grid.eye[1] = eye.y; grid.eye[2] = eye.z;
                for (int corner = 0; corner < 4; corner++) {
                    Vector4f point = projectionInverse.transform(new Vector4f((corner & 1) == 0 ? -1 : 1,
                            (corner & 2) == 0 ? -1 : 1, 1, 1));
                    if (Math.abs(point.w) > 1e-8f) point.div(point.w);
                    Vector3f ray = cameraToWorld.transformDirection(new Vector3f(point.x, point.y, point.z));
                    grid.rays[corner*3]=ray.x;grid.rays[corner*3+1]=ray.y;grid.rays[corner*3+2]=ray.z;
                }
                prepareConstants(grid);
                try (State state = new State()) {
                    ensurePrograms();
                    grid.allocate();
                    render(grid, cameraTransmission);
                }
                if (stable) grid.history.capture(viewWidth, viewHeight, inverseProjection, inverseView,
                        cameraTransmission, worldRevision, entityRevision);
                else grid.history.valid = false;
            }
            status = "ACTIVE"; failure = "";
        } catch (RuntimeException error) {
            grid.history.valid = false; grid.output = 0;
            grid.retryAfter=System.nanoTime()+1_000_000_000L;
            status = "FAILED"; failure = error.toString();
        } finally { rendering = false; }
        if (grid.output != 0) {
            bindings.upload(grid);
            GL20C.glUniform1i(ready, 1);
        }
        return grid.output;
    }

    private static boolean enabled(com.mine.geometry_node.core.node.value.light.LightConfig.Volume v) {
        return !v.mode().equals("off") && v.strength() > 0 && v.density() > 0;
    }

    private static boolean reusableScene() {
        boolean dynamicOccluders = ModelIrisHeldLight.nodeOccluderCount() > 0;
        for(var light:NodeLightRenderer.lights()) {
            var config=light.definition().config();var volume=config.volume();
            if(!enabled(volume))continue;
            if(light.projectionTexture()!=null || config.shadow().enabled() && dynamicOccluders)return false;
            if(volume.mode().equals("noise") && volume.noiseStrength()>0 && volume.velocity().lengthSqr()>0)return false;
        }
        return true;
    }

    private static boolean shadowedVolume() {
        for (var light : NodeLightRenderer.lights())
            if (enabled(light.definition().config().volume()) && light.definition().config().shadow().enabled()) return true;
        return false;
    }

    /** Fixed storage avoids boxing matrices and rebuilding list keys on every frame. */
    private static final class History {
        final float[] projection = new float[16], view = new float[16];
        int width, height, worldTexture;
        long worldRevision, entityRevision;
        boolean valid, transmission;
        Vec3 camera;
        List<ClientLightStore.ResolvedLight> lights = List.of();

        boolean matches(int w, int h, float[] p, float[] v, boolean t, long world, long entity) {
            return valid && width == w && height == h && transmission == t
                    && worldTexture == ModelPlacedLightField.texture() && worldRevision == world && entityRevision == entity
                    && camera.equals(NodeLightRenderer.camera()) && Arrays.equals(projection, p) && Arrays.equals(view, v)
                    && lights.equals(NodeLightRenderer.lights());
        }

        void capture(int w, int h, float[] p, float[] v, boolean t, long world, long entity) {
            width = w; height = h; transmission = t;
            worldTexture = ModelPlacedLightField.texture(); worldRevision = world; entityRevision = entity;
            camera = NodeLightRenderer.camera(); lights = NodeLightRenderer.lights();
            System.arraycopy(p, 0, projection, 0, 16); System.arraycopy(v, 0, view, 0, 16);
            valid = true;
        }
    }

    private static float[] matrix(int p, int location, float[] value, String name) {
        if (location < 0) throw new IllegalStateException("Volume receiver lacks " + name);
        GL20C.glGetUniformfv(p, location, value);
        Matrix4f matrix = new Matrix4f().set(value);
        if (!matrix.isFinite() || Math.abs(matrix.determinant()) < 1e-12f)
            throw new IllegalStateException("Invalid volume camera matrix " + name);
        return value;
    }

    private static boolean layout(Grid g, int width, int height, Vector3f eye,Matrix4f projection) {
        Arrays.fill(g.rects, 0); Arrays.fill(g.shapes, 0); Arrays.fill(g.depths, 0);
        Arrays.fill(g.views,0);
        Arrays.fill(g.ids,"");
        int y = 0, atlasWidth = 1;
        for (int i = 0; i < NodeLightRenderer.lights().size(); i++) {
            var light = NodeLightRenderer.lights().get(i); var q = light.definition().config().quality();
            g.ids[i]=light.id();
            if (!enabled(light.definition().config().volume())) continue;
            float[] view=screenBounds(light,projection,width,height);
            if(view==null)continue;
            System.arraycopy(view,0,g.views,i*4,4);
            int w = NodeLightFroxelQuality.width(Math.max(1,(int)Math.ceil(width*view[2])), q);
            int h = NodeLightFroxelQuality.height(Math.max(1,(int)Math.ceil(height*view[3])), q);
            int slices = NodeLightFroxelQuality.depthSlices(q), columns = NodeLightFroxelQuality.columns(slices);
            g.rects[i*4] = 0; g.rects[i*4+1] = y; g.rects[i*4+2] = w; g.rects[i*4+3] = h;
            g.shapes[i*2] = slices; g.shapes[i*2+1] = columns;
            var relative = light.position().subtract(NodeLightRenderer.camera());
            double distance = Math.sqrt(Math.pow(relative.x-eye.x,2)+Math.pow(relative.y-eye.y,2)+Math.pow(relative.z-eye.z,2));
            double scale = Math.max(light.definition().emitterRadius(), .125), range = light.definition().range();
            g.depths[i*4] = (float)distance; g.depths[i*4+1] = (float)scale;
            g.depths[i*4+2] = (float)asinh((Math.max(0,distance-range)-distance)/scale);
            g.depths[i*4+3] = (float)asinh(range/scale);
            y += h * NodeLightFroxelQuality.rows(slices); atlasWidth = Math.max(atlasWidth, w*columns);
        }
        g.requestedWidth=atlasWidth;g.requestedHeight=Math.max(1,y);
        if (y == 0) {
            g.nextWidth = g.nextHeight = 1;
            return false;
        }
        int limit=GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
        while(atlasWidth>limit || y>limit || (long)atlasWidth*y*48>256L*1024*1024) {
            y=0;atlasWidth=1;
            for(int i=0;i<8;i++) {
                if(g.shapes[i*2]==0)continue;
                g.rects[i*4+1]=y;
                g.rects[i*4+2]=Math.max(1,(g.rects[i*4+2]+1)/2);
                g.rects[i*4+3]=Math.max(1,(g.rects[i*4+3]+1)/2);
                y+=g.rects[i*4+3]*NodeLightFroxelQuality.rows(g.shapes[i*2]);
                atlasWidth=Math.max(atlasWidth,g.rects[i*4+2]*g.shapes[i*2+1]);
            }
        }
        g.nextWidth = atlasWidth; g.nextHeight = Math.max(1, y);
        return true;
    }

    private static float[] screenBounds(ClientLightStore.ResolvedLight light,Matrix4f projection,int width,int height) {
        var relative=light.position().subtract(NodeLightRenderer.camera());double range=light.definition().range();
        boolean spot=light.definition().type().equals("spot");
        double radius=light.definition().emitterRadius(),farRadius=radius+range*Math.tan(Math.toRadians(light.definition().coneAngle()*.5));
        Vector4f[] corners=new Vector4f[8];boolean inFront=false,allInFront=true;
        for(int c=0;c<8;c++) {
            net.minecraft.world.phys.Vec3 point;
            if(spot) {
                // An oriented square frustum contains the disk cone without the
                // oversized projection of its axis-aligned box near the eye.
                double along=(c&4)==0?0:range,extent=((c&4)==0?radius:farRadius)+.002;
                point=relative.add(light.direction().scale(along))
                        .add(light.right().scale((c&1)==0?-extent:extent))
                        .add(light.up().scale((c&2)==0?-extent:extent));
            } else point=relative.add((c&1)==0?-range-.002:range+.002,
                    (c&2)==0?-range-.002:range+.002,(c&4)==0?-range-.002:range+.002);
            corners[c]=projection.transform(new Vector4f((float)point.x,(float)point.y,(float)point.z,1));
            inFront|=corners[c].w>1e-5f;allInFront&=corners[c].w>1e-5f;
        }
        if(!inFront)return null;
        // Side planes only: fog between the eye and the near clip plane still contributes.
        for(int plane=0;plane<4;plane++) {
            boolean outside=true;
            for(Vector4f c:corners) {
                float coordinate=plane<2?c.x:c.y;
                if(((plane&1)==0?coordinate:-coordinate)+c.w>=0){outside=false;break;}
            }
            if(outside)return null;
        }
        if(!allInFront)return new float[]{0,0,1,1};
        float minX=1,minY=1,maxX=0,maxY=0;
        for(Vector4f c:corners) {
            float x=c.x/c.w*.5f+.5f,y=c.y/c.w*.5f+.5f;
            minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);
        }
        minX=Math.max(0,minX-2f/width);maxX=Math.min(1,maxX+2f/width);
        minY=Math.max(0,minY-2f/height);maxY=Math.min(1,maxY+2f/height);
        if(maxX<=minX||maxY<=minY)return null;
        return new float[]{minX,minY,maxX-minX,maxY-minY};
    }

    private static double asinh(double x) { return Math.copySign(Math.log(Math.abs(x)+Math.sqrt(x*x+1)),x); }

    private static void prepareConstants(Grid grid) {
        Arrays.fill(grid.constants,0);
        float spanX=length(grid.rays[3]-grid.rays[0],grid.rays[4]-grid.rays[1],grid.rays[5]-grid.rays[2]);
        float spanY=length(grid.rays[6]-grid.rays[0],grid.rays[7]-grid.rays[1],grid.rays[8]-grid.rays[2]);
        for(int i=0;i<NodeLightRenderer.lights().size();i++) {
            if(grid.shapes[i*2]==0)continue;
            int b=i*4;var light=NodeLightRenderer.lights().get(i);var definition=light.definition();
            // Use the uploaded float depth mapping and camera-relative positions.
            grid.constants[b]=Math.max(0f,grid.depths[b]+grid.depths[b+1]*(float)Math.sinh(grid.depths[b+2]));
            grid.constants[b+1]=Math.max(0f,grid.depths[b]+grid.depths[b+1]*(float)Math.sinh(grid.depths[b+3]));
            var source=light.position().subtract(NodeLightRenderer.camera());
            float distance=length((float)source.x-grid.eye[0],(float)source.y-grid.eye[1],(float)source.z-grid.eye[2]);
            float texelX=grid.views[b+2]/grid.rects[b+2],texelY=grid.views[b+3]/grid.rects[b+3];
            grid.constants[b+2]=0.25f*(distance+definition.range())*(spanX*texelX+spanY*texelY);
            float half=(float)Math.toRadians(definition.coneAngle()*0.5);
            grid.constants[b+3]=definition.emitterRadius()/Math.max((float)Math.tan(half),0.0001f);
        }
    }

    private static float length(float x,float y,float z) {return (float)Math.sqrt(x*x+y*y+z*z);}

    private static void render(Grid g, boolean transmission) {
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, g.fbo);
        GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
        GL11C.glDisable(GL11C.GL_DEPTH_TEST); GL11C.glDisable(GL11C.GL_STENCIL_TEST);
        GL11C.glDisable(GL11C.GL_CULL_FACE); GL11C.glDisable(GL11C.GL_SCISSOR_TEST);
        GL11C.glDisable(GL30C.GL_FRAMEBUFFER_SRGB); GL30C.glDisablei(GL11C.GL_BLEND, 0);
        GL30C.glColorMaski(0,true,true,true,true); GL30C.glBindVertexArray(vao);
        GL20C.glUseProgram(inject.id);
        ModelIrisHeldLight.uploadNativeUniforms(inject.id);
        NodeLightRenderer.upload(inject.id, inject.lights);
        inject.bindings.upload(g);
        bind(inject.world,2,ModelPlacedLightField.texture());
        bind(inject.models,3,ModelIrisHeldLight.nativeTexture());
        bind(inject.lights.entityBvh,4,NodeLightEntityShadows.texture());
        bind(inject.lights.cookies,5,NodeLightRenderer.cookies());
        GL20C.glUniform1i(inject.cameraTransmission,transmission?1:0);
        target(g.textures[0]);
        for(int i=0;i<NodeLightRenderer.lights().size();i++) {
            if(g.shapes[i*2]==0)continue;
            viewport(g,i); GL20C.glUniform1i(inject.index,i);
            if(LightReports.probeFroxel(inject.id,i))probeInjection(inject);
            timed("node_light_froxel_inject"); injections++;
            var light=NodeLightRenderer.lights().get(i);
            LightReports.submittedFroxel(inject.id,"inject",i,g.rects[i*4+2],g.rects[i*4+3],g.shapes[i*2],
                    light.definition().emitterRadius()<=0?1:light.definition().config().quality().volumeEmitterSamples());
        }
        GL20C.glUseProgram(scan.id); scan.bindings.upload(g);
        for(int i=0;i<NodeLightRenderer.lights().size();i++) {
            int slices=g.shapes[i*2]; if(slices==0)continue;
            viewport(g,i); GL20C.glUniform1i(scan.index,i);
            if(slices<=32 && (long)g.rects[i*4+2]*g.rects[i*4+3]*slices<=4096) {
                // Tiny grids benefit from one bounded texture-only prefix draw.
                target(g.textures[1]);bind(scan.lights.volume,0,g.textures[0]);
                GL20C.glUniform1i(scan.offset,0);timed("node_light_froxel_integrate");integrations++;
                LightReports.submittedFroxel(scan.id,"integrate",i,g.rects[i*4+2],g.rects[i*4+3],slices,0);
                continue;
            }
            int passes=32-Integer.numberOfLeadingZeros(slices-1),source=g.textures[0];
            // Pick the first ping-pong target so every light ends in the same final texture.
            int destination=(passes&1)==1?1:2;
            for(int offset=1;offset<slices;offset*=2) {
                target(g.textures[destination]);bind(scan.lights.volume,0,source);
                GL20C.glUniform1i(scan.offset,offset); timed("node_light_froxel_integrate"); integrations++;
                LightReports.submittedFroxel(scan.id,"integrate",i,g.rects[i*4+2],g.rects[i*4+3],slices,0);
                source=g.textures[destination];destination=destination==1?2:1;
            }
        }
        g.output=g.textures[1];
    }

    private static void probeInjection(Program p) {
        var u=p.lights;int[] entities=new int[3];
        if(u.entityRange>=0)GL20C.glGetUniformiv(p.id,u.entityRange,entities);
        int models=u.occluders<0?0:GL20C.glGetUniformi(p.id,u.occluders);
        float[][] lower=new float[8][4],upper=new float[8][4];
        for(int i=0;i<8;i++) {
            if(u.occupiedMin[i]>=0)GL20C.glGetUniformfv(p.id,u.occupiedMin[i],lower[i]);
            if(u.occupiedMax[i]>=0)GL20C.glGetUniformfv(p.id,u.occupiedMax[i],upper[i]);
        }
        int[] masks={1,2,4,7};String[] names={"no_world","no_entity","no_model","no_occlusion"};
        try {
            GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,3);
            for(int i=0;i<masks.length;i++) {
                if(masks[i]==2&&entities[0]==0 || masks[i]==4&&models==0)continue;
                probeUniforms(u,masks[i],entities,models,lower,upper);GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,3);
                probeUniforms(u,0,entities,models,lower,upper);timed("node_light_probe_froxel_"+names[i]+"_reference");
                probeUniforms(u,masks[i],entities,models,lower,upper);timed("node_light_probe_froxel_"+names[i]);
            }
        } finally {probeUniforms(u,0,entities,models,lower,upper);}
        // The caller redraws unmodified injection before any prefix pass consumes it.
    }

    private static void probeUniforms(NodeLightRenderer.Interface u,int mask,int[] entities,int models,float[][] lower,float[][] upper) {
        GL20C.glUniform3i(u.entityRange,(mask&2)==0?entities[0]:0,entities[1],entities[2]);
        GL20C.glUniform1i(u.occluders,(mask&4)==0?models:0);
        for(int i=0;i<8;i++) {
            if((mask&1)!=0)GL20C.glUniform4f(u.occupiedMax[i],lower[i][0],lower[i][1],lower[i][2],1);
            else GL20C.glUniform4fv(u.occupiedMax[i],upper[i]);
        }
    }

    private static void timed(String name) {
        int timer=ModelShaderGpuTimings.begin(name);
        try {GL11C.glDrawArrays(GL11C.GL_TRIANGLES,0,3);} finally {ModelShaderGpuTimings.end(timer);}
    }
    private static void target(int texture) {
        GL30C.glFramebufferTexture2D(GL30C.GL_DRAW_FRAMEBUFFER,GL30C.GL_COLOR_ATTACHMENT0,GL11C.GL_TEXTURE_2D,texture,0);
    }
    private static void viewport(Grid g,int i) {
        GL11C.glViewport(g.rects[i*4],g.rects[i*4+1],g.rects[i*4+2]*g.shapes[i*2+1],
                g.rects[i*4+3]*NodeLightFroxelQuality.rows(g.shapes[i*2]));
    }
    private static void bind(int location,int unit,int texture) {
        GL13C.glActiveTexture(GL13C.GL_TEXTURE0+unit);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,texture);
        GL33C.glBindSampler(unit,0);GL20C.glUniform1i(location,unit);
    }

    /** Uniform addresses live as long as the linked program, not the current world/grid. */
    private static final class Bindings {
        final int ready, projection, view, rects, shapes, depths, constants, views, rays, eye, viewProjection;
        final boolean cameraTransmission;
        final float[] projectionValue = new float[16], viewValue = new float[16];

        Bindings(int program) {
            ready = GL20C.glGetUniformLocation(program,"nodeFroxelReady");
            int packProjection = GL20C.glGetUniformLocation(program,"gbufferProjectionInverse");
            projection = packProjection >= 0 ? packProjection : GL20C.glGetUniformLocation(program,"nativeProjectionInverse");
            view = GL20C.glGetUniformLocation(program,"gbufferModelViewInverse");
            cameraTransmission = GL20C.glGetUniformLocation(program,"nativeDepth") >= 0
                    || GL20C.glGetUniformLocation(program,"nodeFroxelCameraAbsorption") >= 0;
            rects = GL20C.glGetUniformLocation(program,"nodeFroxelRects[0]");
            shapes = GL20C.glGetUniformLocation(program,"nodeFroxelShape[0]");
            depths = GL20C.glGetUniformLocation(program,"nodeFroxelDepths[0]");
            constants = GL20C.glGetUniformLocation(program,"nodeFroxelConstants[0]");
            views = GL20C.glGetUniformLocation(program,"nodeFroxelViews[0]");
            rays = GL20C.glGetUniformLocation(program,"nodeFroxelRays[0]");
            eye = GL20C.glGetUniformLocation(program,"nodeEyeOffset");
            viewProjection = GL20C.glGetUniformLocation(program,"nodeFroxelViewProjection");
        }

        void upload(Grid grid) {
            GL20C.glUniform4iv(rects,grid.rects);
            GL20C.glUniform2iv(shapes,grid.shapes);
            GL20C.glUniform4fv(depths,grid.depths);
            GL20C.glUniform4fv(constants,grid.constants);
            GL20C.glUniform4fv(views,grid.views);
            GL20C.glUniform3fv(rays,grid.rays);
            GL20C.glUniform3fv(eye,grid.eye);
            GL20C.glUniformMatrix4fv(viewProjection,false,grid.viewProjection);
        }
    }

    private static void ensurePrograms() {
        if(inject!=null&&scan!=null)return;
        try {
            inject=new Program(true);scan=new Program(false);vao=GL30C.glGenVertexArrays();
        } catch(RuntimeException error) {close();throw error;}
    }
    private static final class Program {
        final int id,index,offset,cameraTransmission,world,models;
        final NodeLightRenderer.Interface lights;
        final Bindings bindings;
        Program(boolean injection) {
            String body=injection?"""
                void main(){ivec4 r=nodeFroxelRects[nodeLightIndex];ivec2 p=ivec2(gl_FragCoord.xy)-r.xy;
                ivec2 tile=p/r.zw;int slice=tile.y*nodeFroxelShape[nodeLightIndex].y+tile.x;
                vec2 uv=(vec2(p-tile*r.zw)+0.5)/vec2(r.zw);color=nodeFroxelInject(nodeLightIndex,uv,slice);}
                """:"void main(){color=nodeFroxelScan(ivec2(gl_FragCoord.xy),nodeLightIndex,nodeFroxelScanOffset);}";
            String fragment="#version 330 core\n"+(injection?"#define NODE_LIGHT_FROXEL_INJECT\n":"")
                    +ModelSundialHeldLight.declarations()+NodeLightShader.declarations()
                    +"\nuniform int nodeFroxelScanOffset;out vec4 color;\n"+body;
            id=NodeLightPrograms.link(VERTEX,fragment).id();lights=new NodeLightRenderer.Interface(id);bindings=new Bindings(id);
            index=GL20C.glGetUniformLocation(id,"nodeLightIndex");offset=GL20C.glGetUniformLocation(id,"nodeFroxelScanOffset");
            cameraTransmission=GL20C.glGetUniformLocation(id,"nodeFroxelCameraTransmission");
            world=GL20C.glGetUniformLocation(id,"geometry_node_blockWorld");
            models=GL20C.glGetUniformLocation(id,"geometry_node_lightBvh");
        }
    }

    private static final class Grid {
        final String[] ids=new String[8];
        final int[] rects=new int[32],shapes=new int[16],textures=new int[3];
        final float[] depths=new float[32],constants=new float[32],views=new float[32],rays=new float[12],eye=new float[3],viewProjection=new float[16];
        final History history = new History();
        int width,height,nextWidth,nextHeight,requestedWidth,requestedHeight,fbo,output;long bytes,retryAfter,allocations;
        void allocate() {
            // Cropped light bounds move every frame. Retain capacity instead of
            // reallocating textures for every one-pixel change of the projected cone.
            if(width>=nextWidth&&height>=nextHeight&&fbo!=0)return;
            int limit=GL11C.glGetInteger(GL11C.GL_MAX_TEXTURE_SIZE);
            int allocationWidth=Math.min(limit,(Math.max(width,nextWidth)+63)/64*64);
            int allocationHeight=Math.min(limit,(Math.max(height,nextHeight)+63)/64*64);
            if((long)allocationWidth*allocationHeight*48>256L*1024*1024) {
                allocationWidth=nextWidth;allocationHeight=nextHeight;
            }
            long required=(long)allocationWidth*allocationHeight*48;
            if(nextWidth>limit||nextHeight>limit||required>256L*1024*1024)
                throw new IllegalStateException("Froxel atlas exceeds texture extent or 256 MiB budget");
            release();
            if(!ModelRenderAsset.residency().reserveTransientGpu(required))throw new IllegalStateException("Froxel GPU budget exhausted");
            bytes=required;width=allocationWidth;height=allocationHeight;allocations++;
            try {
                GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,0);
                fbo=GL30C.glGenFramebuffers();GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,fbo);
                GL20C.glDrawBuffers(GL30C.GL_COLOR_ATTACHMENT0);
                for(int i=0;i<textures.length;i++) {
                    textures[i]=GL11C.glGenTextures();GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[i]);
                    // Prefix differences behind glass must retain weak distant fog after a bright source.
                    GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D,0,GL30C.GL_RGBA32F,width,height,0,GL11C.GL_RGBA,GL11C.GL_FLOAT,0L);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MIN_FILTER,GL11C.GL_NEAREST);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL11C.GL_TEXTURE_MAG_FILTER,GL11C.GL_NEAREST);
                    GL11C.glTexParameteri(GL11C.GL_TEXTURE_2D,GL12C.GL_TEXTURE_MAX_LEVEL,0);
                    target(textures[i]);
                    if(GL30C.glCheckFramebufferStatus(GL30C.GL_DRAW_FRAMEBUFFER)!=GL30C.GL_FRAMEBUFFER_COMPLETE)
                        throw new IllegalStateException("Incomplete froxel framebuffer");
                }
            } catch(RuntimeException error) {release();throw error;}
        }
        void release() {
            int[] old=textures.clone();int oldFbo=fbo;long retired=bytes;
            NodeLightTargets.retiredVolume(output);
            Arrays.fill(textures,0);fbo=output=width=height=0;bytes=0;history.valid=false;history.lights=List.of();
            if(retired!=0)RenderSystem.queueFencedTask(()->{
                for(int t:old)if(t!=0)GL11C.glDeleteTextures(t);
                if(oldFbo!=0)GL30C.glDeleteFramebuffers(oldFbo);
                ModelRenderAsset.residency().releaseTransientGpu(retired);
            });
        }
    }

    static void deleted(int program) {RECEIVERS.remove(program);Grid g=GRIDS.remove(program);if(g!=null)g.release();}
    static void clear() {for(Grid g:GRIDS.values())g.release();GRIDS.clear();status="IDLE";}
    static void close() {
        clear();RECEIVERS.clear();int a=inject==null?0:inject.id,b=scan==null?0:scan.id,v=vao;inject=scan=null;vao=0;
        if(a!=0)ModelIrisHeldLight.deleted(a);if(b!=0)ModelIrisHeldLight.deleted(b);
        if(a!=0||b!=0||v!=0)RenderSystem.queueFencedTask(()->{GL20C.glDeleteProgram(a);GL20C.glDeleteProgram(b);GL30C.glDeleteVertexArrays(v);});
    }
    public static Map<String,Object> report() {
        var out=new LinkedHashMap<String,Object>();out.put("status",status);out.put("failure",failure);
        out.put("gpuBytes",GRIDS.values().stream().mapToLong(g->g.bytes).sum());out.put("receivers",GRIDS.size());
        out.put("injectionDraws",injections);out.put("integrationDraws",integrations);out.put("reusedFrames",reuses);
        out.put("historyStatus",historyReason);out.put("historyPolicy","Exact unchanged-view, world-blocker and entity-geometry reuse. Active volumes rebuild for animated medium, cookies or shadow-dependent model occluders. Entity uploads, active ranges and captured bounds invalidate shadowed volumes. No moving-camera reprojection or historical blending; fully offscreen volumes skip GPU setup.");
        out.put("grids",GRIDS.entrySet().stream().map(entry->{
            Grid g=entry.getValue();var lights=new ArrayList<Map<String,Object>>();
            for(int i=0;i<8;i++)if(g.shapes[i*2]>0)lights.add(Map.of("index",i,
                    "resourceId",g.ids[i],
                    "dimensions",List.of(g.rects[i*4+2],g.rects[i*4+3],g.shapes[i*2]),
                    "screenBounds",List.of(g.views[i*4],g.views[i*4+1],g.views[i*4+2],g.views[i*4+3]),
                    "depthMapping",List.of(g.depths[i*4],g.depths[i*4+1],g.depths[i*4+2],g.depths[i*4+3])));
            return Map.of("receiverProgram",entry.getKey(),"atlas",List.of(g.width,g.height),
                    "requestedAtlas",List.of(g.requestedWidth,g.requestedHeight),"budgetReduced",g.nextWidth<g.requestedWidth||g.nextHeight<g.requestedHeight,
                    "allocations",g.allocations,
                    "gpuBytes",g.bytes,"lights",lights);
        }).toList());
        out.put("scope","Per-light radial froxels, finite emitters and shared occlusion, parallel prefix transport, depth-prefix lookup without full-resolution raymarch fallback.");
        return out;
    }

    private static final class State implements AutoCloseable {
        final int program=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM),vao=GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        final int fbo=GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING),active=GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        final int unpack=GL11C.glGetInteger(GL21C.GL_PIXEL_UNPACK_BUFFER_BINDING),activeTexture=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        final int[] viewport=new int[4],textures=new int[6],samplers=new int[6];
        final int[] caps={GL11C.GL_DEPTH_TEST,GL11C.GL_STENCIL_TEST,GL11C.GL_CULL_FACE,GL11C.GL_SCISSOR_TEST,GL30C.GL_FRAMEBUFFER_SRGB};
        final boolean[] enabled=new boolean[caps.length];final boolean blend=GL30C.glIsEnabledi(GL11C.GL_BLEND,0);
        final byte[] mask=new byte[4];
        State() {
            GL11C.glGetIntegerv(GL11C.GL_VIEWPORT,viewport);
            try(var stack=org.lwjgl.system.MemoryStack.stackPush()){var b=stack.malloc(4);GL30C.glGetBooleani_v(GL11C.GL_COLOR_WRITEMASK,0,b);b.get(mask);}
            for(int i=0;i<caps.length;i++)enabled[i]=GL11C.glIsEnabled(caps[i]);
            for(int i=0;i<6;i++){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+i);textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);samplers[i]=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,i);}
            GL13C.glActiveTexture(active);
        }
        public void close() {
            GL20C.glUseProgram(program);GL30C.glBindVertexArray(vao);GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER,fbo);
            GL11C.glViewport(viewport[0],viewport[1],viewport[2],viewport[3]);
            for(int i=0;i<caps.length;i++)if(enabled[i])GL11C.glEnable(caps[i]);else GL11C.glDisable(caps[i]);
            if(blend)GL30C.glEnablei(GL11C.GL_BLEND,0);else GL30C.glDisablei(GL11C.GL_BLEND,0);
            GL30C.glColorMaski(0,mask[0]!=0,mask[1]!=0,mask[2]!=0,mask[3]!=0);
            for(int i=0;i<6;i++){GL13C.glActiveTexture(GL13C.GL_TEXTURE0+i);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[i]);GL33C.glBindSampler(i,samplers[i]);}
            GL13C.glActiveTexture(active);GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,activeTexture);
            GL15C.glBindBuffer(GL21C.GL_PIXEL_UNPACK_BUFFER,unpack);
        }
    }
}
