package com.mine.geometry_node.client.runtime.light.render;

import com.mine.geometry_node.client.runtime.light.diagnostic.LightReports;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelIrisHeldLight;
import com.mine.geometry_node.client.runtime.model.render.bvh.ModelPlacedLightField;
import org.lwjgl.opengl.*;

/** Native terrain passes share node lights and the same world/model blockers as model receivers. */
public final class NativeTerrainLights {
    private static Binding active;
    private static int terrainTimer = -1;
    private static boolean enabled;
    private static long passes;
    private static String failure = "";
    private static final java.util.Set<Integer> loggedPrograms = new java.util.HashSet<>();
    private static final java.util.Map<Integer,Locations> programs = new java.util.HashMap<>();

    private NativeTerrainLights() {}
    static int cachedProgramCount() { return programs.size(); }
    static void clearCachedLocations() { programs.clear(); loggedPrograms.clear(); }

    public static void setEnabled(boolean value) { enabled=value; }
    public static boolean enabled() { return enabled; }

    public static void begin() {
        begin(true);
    }

    static void beginVolume() { begin(false); }

    private static void begin(boolean terrain) {
        if (!enabled || active != null || !NodeLightRenderer.active()) return;
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        Locations locations = programs.computeIfAbsent(program, Locations::new);
        if (locations.marker < 0) return;
        Binding binding = new Binding(program, locations);
        try {
            binding.bind();
            active = binding;
            if (terrain && LightReports.capturing())
                terrainTimer = com.mine.geometry_node.client.runtime.model.render.diagnostic.ModelShaderGpuTimings.begin("node_light_native_terrain");
            passes++;
            if(binding.nodes!=null && loggedPrograms.size()<8 && loggedPrograms.add(program))
                com.mine.geometry_node.GeometryNode.LOGGER.info("Node light native terrain receiver bound: program={}, lights={}",program,NodeLightRenderer.lights().size());
            failure = "";
        } catch (RuntimeException error) {
            binding.close();
            failure = error.toString();
        }
    }

    public static void end() {
        com.mine.geometry_node.client.runtime.model.render.diagnostic.ModelShaderGpuTimings.end(terrainTimer);
        terrainTimer = -1;
        Binding binding = active;
        active = null;
        if (binding != null) binding.close();
    }

    public static void draw(Runnable draw) {
        try (var grid = com.mine.geometry_node.client.runtime.game.render.ViewportSurfaceGrid.bind()) {
            drawWithLights(draw);
        }
    }

    private static void drawWithLights(Runnable draw) {
        if (active != null) { draw.run(); return; }
        begin();
        try { draw.run(); }
        finally { end(); }
    }

    public static java.util.Map<String,Object> report() {
        return java.util.Map.of("passes",passes,"failure",failure);
    }

    public static void deleted(int program) { com.mine.geometry_node.client.runtime.game.render.ViewportSurfaceGrid.deleted(program); programs.remove(program); loggedPrograms.remove(program); }

    private static final class Locations {
        final int marker, translucent, bvh, world;
        Locations(int program) {
            marker = GL20C.glGetUniformLocation(program, "nodeTerrainReceiver");
            translucent = marker < 0 ? -1 : GL20C.glGetUniformLocation(program, "nodeTerrainTranslucent");
            bvh = marker < 0 ? -1 : GL20C.glGetUniformLocation(program, "geometry_node_lightBvh");
            world = marker < 0 ? -1 : GL20C.glGetUniformLocation(program, "geometry_node_blockWorld");
        }
    }

    private static final class Binding implements AutoCloseable {
        private final int program, marker, activeUnit, translucent;
        private final int[] textures = new int[2], samplers = new int[2], locations = new int[2], uniforms = new int[2];
        private NodeLightRenderer.NativeBinding nodes;
        Binding(int program, Locations cached) {
            this.program = program; this.marker = cached.marker;
            translucent = cached.translucent;
            activeUnit = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
            for (int i=0;i<2;i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0+14+i);
                textures[i]=GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
                samplers[i]=GL30C.glGetIntegeri(GL33C.GL_SAMPLER_BINDING,14+i);
                locations[i]=i==0?cached.bvh:cached.world;
                uniforms[i]=locations[i]<0?0:GL20C.glGetUniformi(program,locations[i]);
            }
            GL13C.glActiveTexture(activeUnit);
        }
        void bind() {
            for (int i=0;i<2;i++) {
                GL13C.glActiveTexture(GL13C.GL_TEXTURE0+14+i);
                GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,i==0?ModelIrisHeldLight.nativeTexture():ModelPlacedLightField.texture());
                GL33C.glBindSampler(14+i,0);
                GL20C.glUniform1i(locations[i],14+i);
            }
            GL13C.glActiveTexture(activeUnit);
            ModelIrisHeldLight.uploadNativeUniforms(program);
            nodes = NodeLightRenderer.bindNative();
            GL20C.glUniform1i(marker,nodes==null?0:1);
            GL20C.glUniform1i(translucent,GL30C.glIsEnabledi(GL11C.GL_BLEND,0)?1:0);
            if (nodes!=null) NodeLightRenderer.recordNativeDraw();
        }
        @Override public void close() {
            int previousProgram=GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
            if(previousProgram!=program)GL20C.glUseProgram(program);
            try {
                GL20C.glUniform1i(marker,0);
                GL20C.glUniform1i(translucent,0);
                if(nodes!=null)nodes.close();
                for(int i=0;i<2;i++) {
                    GL20C.glUniform1i(locations[i],uniforms[i]);
                    GL13C.glActiveTexture(GL13C.GL_TEXTURE0+14+i);
                    GL11C.glBindTexture(GL11C.GL_TEXTURE_2D,textures[i]);
                    GL33C.glBindSampler(14+i,samplers[i]);
                }
                GL13C.glActiveTexture(activeUnit);
            } finally {
                if(previousProgram!=program)GL20C.glUseProgram(previousProgram);
            }
        }
    }
}
