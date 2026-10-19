package com.mine.geometry_node.core.engine.system.scene;

import net.minecraft.world.phys.Vec3;
import com.mine.geometry_node.core.engine.metadata.OwnerMetadata;
import net.minecraft.core.HolderLookup;
import java.util.*;

/** Immutable common envelope. Type-owned scalar parameters are validated by the registered schema. */
public record SceneObject(UUID id, String typeId, String name, Vec3 position, Vec3 color,
                          boolean enabled, long revision, Map<String,String> parameters, Vec3 rotation, Vec3 scale, OwnerMetadata metadata) {
    public static final String POINT_LIGHT="point_light";
    public static final int MAX_PARAMETERS = 32;
    public SceneObject {
        Objects.requireNonNull(metadata);
        Objects.requireNonNull(id);Objects.requireNonNull(typeId);Objects.requireNonNull(name);
        if(typeId.isBlank()||typeId.length()>64||name.isBlank()||name.length()>96||revision<0)
            throw new IllegalArgumentException("Invalid scene identity");
        if(!finite(position)||Math.abs(position.x)>30_000_000||Math.abs(position.y)>30_000_000||Math.abs(position.z)>30_000_000)
            throw new IllegalArgumentException("Invalid scene position");
        if(!finite(color)||color.x<0||color.y<0||color.z<0||color.x>1||color.y>1||color.z>1)
            throw new IllegalArgumentException("Invalid scene color");
        if(!finite(rotation)||Math.max(Math.max(Math.abs(rotation.x),Math.abs(rotation.y)),Math.abs(rotation.z))>1e6||!finite(scale)||scale.x<.001||scale.y<.001||scale.z<.001||scale.x>1024||scale.y>1024||scale.z>1024)throw new IllegalArgumentException("Invalid transform");
        parameters=Map.copyOf(parameters);
        if(parameters.size()>MAX_PARAMETERS)throw new IllegalArgumentException("Too many scene parameters");
        parameters.forEach((key,value)->{if(key.isBlank()||key.length()>64||value.length()>256)throw new IllegalArgumentException("Invalid scene parameter");});
    }
    public SceneObject(UUID id,String type,String name,Vec3 position,Vec3 color,boolean enabled,long revision,Map<String,String> parameters,Vec3 rotation,Vec3 scale){
        this(id,type,name,position,color,enabled,revision,parameters,rotation,scale,OwnerMetadata.EMPTY);
    }
    public SceneObject(UUID id,String type,String name,Vec3 position,Vec3 color,boolean enabled,long revision,Map<String,String> parameters){
        this(id,type,name,position,color,enabled,revision,parameters,Vec3.ZERO,new Vec3(1,1,1));
    }
    public SceneObject withTransform(Vec3 position,Vec3 rotation,Vec3 scale){return new SceneObject(id,typeId,name,position,color,enabled,revision,parameters,rotation,scale,metadata);}
    public List<String> tags(){return metadata.tags();}
    public Map<String,Object> data(){return data(null);}
    public Map<String,Object> data(HolderLookup.Provider registries){return metadata.data(registries);}
    public SceneObject withMetadata(OwnerMetadata value){return new SceneObject(id,typeId,name,position,color,enabled,revision,parameters,rotation,scale,value);}
    public SceneObject withMetadata(Collection<String> tags,Map<String,?> data,HolderLookup.Provider registries){return withMetadata(OwnerMetadata.of(tags,data,registries));}
    public double number(String key){return Double.parseDouble(parameters.get(key));}
    public float intensity(){return (float)number("intensity");}
    public float range(){return (float)number("range");}
    public static SceneObject pointLight(UUID id,Vec3 position){return SceneObjectTypes.require(POINT_LIGHT).create(id,position);}
    public SceneObject withPosition(Vec3 value){return new SceneObject(id,typeId,name,value,color,enabled,revision,parameters,rotation,scale,metadata);}
    public SceneObject withRevision(long value){return new SceneObject(id,typeId,name,position,color,enabled,value,parameters,rotation,scale,metadata);}
    public SceneObject withProperties(String name,Vec3 color,boolean enabled,Map<String,String> parameters){
        return new SceneObject(id,typeId,name,position,color,enabled,revision,parameters,rotation,scale,metadata);
    }
    public boolean sameContent(SceneObject other){return other!=null&&withRevision(0).equals(other.withRevision(0));}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
