package com.mine.geometry_node.core.engine.system.scene;

import java.util.*;
import java.util.function.*;
import net.minecraft.world.phys.*;
import com.mine.geometry_node.core.engine.system.scene.geometry.SceneGeometryTypes;
import com.mine.geometry_node.core.engine.system.scene.property.SceneNodeInputs;

/** Common type plugin contract; contains no client/UI or render dependencies. */
public final class SceneObjectTypes {
    public enum Storage { AUTHORED, NATIVE }
    /** Lifecycle-only targets can move/tick independently; history still requires the exact incarnation version. */
    public enum HistoryPolicy { CONTENT, LIFECYCLE }
    public record Type(String id,String labelKey,String category,List<SceneProperty> properties,
                       Function<SceneObject,AABB> bounds,Consumer<SceneObject> validate,Storage storage,boolean creatable,HistoryPolicy historyPolicy) {
        public Type {
            Objects.requireNonNull(id);Objects.requireNonNull(labelKey);Objects.requireNonNull(category);
            Objects.requireNonNull(bounds);Objects.requireNonNull(validate);Objects.requireNonNull(storage);Objects.requireNonNull(historyPolicy);properties=List.copyOf(properties);
            Set<String> keys=new HashSet<>();for(var p:properties)if(!keys.add(p.key()))throw new IllegalArgumentException("Duplicate property");
        }
        public Type(String id,String labelKey,String category,List<SceneProperty> properties,
                    Function<SceneObject,AABB> bounds,Consumer<SceneObject> validate) {
            this(id,labelKey,category,properties,bounds,validate,Storage.AUTHORED,true,HistoryPolicy.CONTENT);
        }
        public Type(String id,String labelKey,String category,List<SceneProperty> properties,
                    Function<SceneObject,AABB> bounds,Consumer<SceneObject> validate,Storage storage) {
            this(id,labelKey,category,properties,bounds,validate,storage,true,HistoryPolicy.CONTENT);
        }
        public Type(String id,String labelKey,String category,List<SceneProperty> properties,
                    Function<SceneObject,AABB> bounds,Consumer<SceneObject> validate,Storage storage,boolean creatable) {
            this(id,labelKey,category,properties,bounds,validate,storage,creatable,HistoryPolicy.CONTENT);
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
                SceneNodeInputs.properties(SceneObject.POINT_LIGHT),
                o->new AABB(o.position().subtract(.5,.5,.5),o.position().add(.5,.5,.5)),o->{}));
        SceneGeometryTypes.register();
        com.mine.geometry_node.core.engine.system.scene.volume.SceneVolumeTypes.register();
        register(new Type(SceneNativeInteractions.TYPE_ID,"geometry_node.game_view.scene.layer.interaction","",
                SceneNodeInputs.properties(SceneNativeInteractions.TYPE_ID),
                SceneNativeInteractions::bounds,SceneNativeInteractions::validate,Storage.NATIVE));
        register(SceneNativeEntities.type());
        com.mine.geometry_node.core.engine.system.scene.property.SceneInspectorSchemas.register(SceneNativeEntities.TYPE_ID,SceneNativeEntities::schema);
        SceneObjectCapabilities.register(SceneNativeEntities.TYPE_ID, Set.of(), SceneObject::withTransform);
    }
    private SceneObjectTypes(){}
    public static synchronized void register(Type type){if(TYPES.putIfAbsent(type.id(),type)!=null)throw new IllegalArgumentException("Duplicate scene type: "+type.id());}
    public static synchronized List<Type> all(){return List.copyOf(TYPES.values());}
    public static synchronized Type require(String id){Type type=TYPES.get(id);if(type==null)throw new IllegalArgumentException("Unsupported scene object type: "+id);return type;}
    public static boolean sameHistoryState(SceneObject a,SceneObject b){
        if(a==null||b==null)return a==b;
        if(!a.id().equals(b.id())||!a.typeId().equals(b.typeId()))return false;
        return require(a.typeId()).historyPolicy()==HistoryPolicy.LIFECYCLE||a.sameContent(b);
    }
    public static void validate(SceneObject object){
        Type type=require(object.typeId());
        if(type.storage()==Storage.NATIVE&&!object.metadata().isEmpty())throw new IllegalArgumentException("Native metadata belongs to the entity owner");
        if(object.parameters().size()!=type.properties().size())throw new IllegalArgumentException("Invalid parameter schema");
        for(var p:type.properties()){String value=object.parameters().get(p.key());if(value==null)throw new IllegalArgumentException("Missing "+p.key());p.validate().accept(value);}
        type.validate().accept(object);
    }
}
