package com.mine.geometry_node.core.engine.system.scene;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.phys.*;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneGeometryTypes;

/** Common type plugin contract; contains no client/UI or render dependencies. */
public final class SceneObjectTypes {
    public record Type(String id,String labelKey,String category,List<SceneProperty> properties,
                       Function<SceneObject,AABB> bounds,Consumer<SceneObject> validate) {
        public Type {
            Objects.requireNonNull(id);Objects.requireNonNull(labelKey);Objects.requireNonNull(category);
            Objects.requireNonNull(bounds);Objects.requireNonNull(validate);properties=List.copyOf(properties);
            Set<String> keys=new HashSet<>();for(var p:properties)if(!keys.add(p.key()))throw new IllegalArgumentException("Duplicate property");
        }
        public SceneObject create(UUID id,Vec3 position){
            Map<String,String> values=new LinkedHashMap<>();for(var p:properties)values.put(p.key(),p.defaultValue());
            SceneObject object=new SceneObject(id,this.id,this.id,position,new Vec3(1,1,1),true,0,values);
            SceneObjectTypes.validate(object);return object;
        }
    }
    private static final Map<String,Type> TYPES=new LinkedHashMap<>();
    static {
        register(new Type(SceneObject.POINT_LIGHT,"geometry_node.game_view.scene.point_light","",
                List.of(SceneProperty.number("intensity",3,0,1024,false),SceneProperty.number("range",16,.01,32,false)),
                o->new AABB(o.position().subtract(.5,.5,.5),o.position().add(.5,.5,.5)),o->{}));
        SceneGeometryTypes.register();
    }
    private SceneObjectTypes(){}
    public static synchronized void register(Type type){if(TYPES.putIfAbsent(type.id(),type)!=null)throw new IllegalArgumentException("Duplicate scene type: "+type.id());}
    public static synchronized List<Type> all(){return List.copyOf(TYPES.values());}
    public static synchronized Type require(String id){Type type=TYPES.get(id);if(type==null)throw new IllegalArgumentException("Unsupported scene object type: "+id);return type;}
    public static void validate(SceneObject object){
        Type type=require(object.typeId());
        if(object.parameters().size()!=type.properties().size())throw new IllegalArgumentException("Invalid parameter schema");
        for(var p:type.properties()){String value=object.parameters().get(p.key());if(value==null)throw new IllegalArgumentException("Missing "+p.key());p.validate().accept(value);}
        type.validate().accept(object);
    }
}
