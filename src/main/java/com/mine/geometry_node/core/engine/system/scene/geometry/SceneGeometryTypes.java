package com.mine.geometry_node.core.engine.system.scene.geometry;

import com.mine.geometry_node.core.engine.system.scene.*;
import com.mine.geometry_node.core.node.value.geometry.GeometryValue;
import net.minecraft.world.phys.*;
import java.util.*;
import java.util.function.Function;

/** Parametric geometry plugins reuse the graph geometry primitives, without a graph instance. */
public final class SceneGeometryTypes {
    public record Geometry(String typeId,Function<SceneObject,GeometryValue.Primitive> primitive) {}
    private static final Map<String,Geometry> TYPES=new LinkedHashMap<>();
    private SceneGeometryTypes(){}
    public static void register(){
        register("cube",List.of(size("width",1),size("height",1),size("depth",1)),
                o->GeometryValue.Primitive.cube(Vec3.ZERO,new Vec3(o.number("width"),o.number("height"),o.number("depth")),2,2,2));
        register("cylinder",List.of(size("radius",.5),size("height",1),SceneProperty.number("segments",32,3,64,true)),
                o->GeometryValue.Primitive.cylinder(Vec3.ZERO,(int)o.number("segments"),1,1,(float)o.number("radius"),(float)o.number("height"),GeometryValue.CylinderFillType.NGON));
        register("uv_sphere",List.of(size("radius",.5),SceneProperty.number("segments",32,3,64,true),SceneProperty.number("rings",16,2,32,true)),
                o->GeometryValue.Primitive.uvSphere(Vec3.ZERO,(int)o.number("segments"),(int)o.number("rings"),(float)o.number("radius")));
    }
    public static synchronized void register(String name,List<SceneProperty> fields,Function<SceneObject,GeometryValue.Primitive> primitive){
        String id="geometry/"+name;
        if(TYPES.containsKey(id))throw new IllegalArgumentException("Duplicate geometry: "+id);
        SceneObjectTypes.register(new SceneObjectTypes.Type(id,"geometry_node.game_view.scene."+name,"geometry",fields,
                o->{Vec3 half=primitive.apply(o).size().scale(.5);return new AABB(o.position().subtract(half),o.position().add(half));},o->{}));
        TYPES.put(id,new Geometry(id,primitive));
    }
    public static Geometry find(String typeId){SceneObjectTypes.all();synchronized(SceneGeometryTypes.class){return TYPES.get(typeId);}}
    private static SceneProperty size(String key,double value){return SceneProperty.number(key,value,.01,256,false);}
}
