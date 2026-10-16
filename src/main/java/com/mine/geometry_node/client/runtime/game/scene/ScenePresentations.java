package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.core.engine.system.scene.*;
import java.util.*;

/** Client layer assignment is independent of the server's schema and the editor's property forms. */
public final class ScenePresentations {
    private static final Map<String,Presentation> TYPES=new LinkedHashMap<>();
    static {register(SceneObject.POINT_LIGHT,new Presentation(ViewportVisualization.Layer.LIGHT,true));}
    private ScenePresentations(){}
    public record Presentation(ViewportVisualization.Layer layer,boolean pointMarker) {}
    public static synchronized void register(String type,Presentation presentation){if(TYPES.putIfAbsent(type,presentation)!=null)throw new IllegalArgumentException("Duplicate presentation");}
    public static synchronized ViewportVisualization.Layer layer(SceneObject object){
        var explicit=TYPES.get(object.typeId());
        if(explicit!=null)return explicit.layer();
        return SceneObjectTypes.require(object.typeId()).category().equals("geometry")?ViewportVisualization.Layer.GEOMETRY:null;
    }
    public static synchronized boolean pointMarker(SceneObject object){var p=TYPES.get(object.typeId());return p!=null&&p.pointMarker();}
    public static synchronized boolean selectable(ViewportVisualization.Layer layer){return layer==ViewportVisualization.Layer.GEOMETRY||TYPES.values().stream().anyMatch(p->p.layer()==layer);}
}
