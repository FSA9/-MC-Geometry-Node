package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneGeometryTypes;
import com.mine.geometry_node.core.engine.graph.debug.geometry.*;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Authored geometry shares primitive tessellation and the existing world geometry pipeline.
 * Mesh cache includes rotation/scale; moving/coloring does not rebake vertices. */
public final class SceneGeometryInputs {
    private record Key(String type,Map<String,String> parameters,Vec3 rotation,Vec3 scale) {}
    private static final Map<Key,GeometryDebugElement> CACHE=new LinkedHashMap<>(32,.75f,true);
    private static long generation=Long.MIN_VALUE;
    private SceneGeometryInputs(){}
    public static List<PacketGeometryDebugSnapshot.Mesh> visibleMeshes(){
        var level=Minecraft.getInstance().level;
        if(level==null){CACHE.clear();return List.of();}
        var snapshot=EditViewportRuntime.renderedScene();
        if(snapshot==null)snapshot=ClientSceneService.INSTANCE.confirmedSnapshot();
        if(generation!=snapshot.worldGeneration()){generation=snapshot.worldGeneration();CACHE.clear();}
        if(!level.dimension().identifier().toString().equals(snapshot.dimension()))return List.of();
        List<PacketGeometryDebugSnapshot.Mesh> result=new ArrayList<>();
        for(var object:snapshot.objects()){
            if(!object.enabled())continue;
            var plugin=SceneGeometryTypes.find(object.typeId());if(plugin==null)continue;
            if(ViewportVisualization.inEditorPass()&&!ViewportVisualization.current().visible(ScenePresentations.layer(object)))continue;
            Key key=new Key(object.typeId(),object.parameters(),object.rotation(),object.scale());
            GeometryDebugElement mesh=CACHE.get(key);
            if(mesh==null){
                mesh=GeometryDebugMeshFactory.buildPrimitiveMesh("scene","scene",plugin.primitive().apply(object),Vec3.ZERO);
                float[] vertices=mesh.vertices().clone();
                for(int i=0;i<vertices.length;i+=3){var v=SceneTransforms.local(object,new Vec3(vertices[i],vertices[i+1],vertices[i+2]));vertices[i]=(float)v.x;vertices[i+1]=(float)v.y;vertices[i+2]=(float)v.z;}
                mesh=new GeometryDebugElement(mesh.id(),mesh.graphId(),mesh.type(),mesh.color(),mesh.showPoints(),mesh.center(),mesh.size(),Vec3.ZERO,vertices,mesh.edges(),mesh.faces());
                CACHE.put(key,mesh);while(CACHE.size()>SceneDocument.MAX_OBJECTS)CACHE.remove(CACHE.keySet().iterator().next());
            }
            int color=0xFF000000|((int)(object.color().x*255)<<16)|((int)(object.color().y*255)<<8)|(int)(object.color().z*255);
            Vec3 p=object.position(),size=mesh.size();
            result.add(new PacketGeometryDebugSnapshot.Mesh(object.id().toString(),"authored_scene",mesh.type(),color,false,
                    p.x,p.y,p.z,size.x,size.y,size.z,0,0,0,mesh.vertices(),mesh.edges(),mesh.faces()));
        }
        return result;
    }
}
