package com.mine.geometry_node.client.ui.editor.game.scene;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import com.mine.geometry_node.client.runtime.game.snap.*;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import java.util.*;
import java.util.function.Consumer;

/** One shared snap session for modal movement and axis-handle dragging. */
final class ViewportMoveSnap {
    private static final java.util.concurrent.atomic.AtomicLong TOKENS=new java.util.concurrent.atomic.AtomicLong();
    private final ViewportVisualization visibility;
    private Consumer<ViewportSnapGeometry.Query> publish=query->{};
    private ViewportSnapSettings settings=ViewportSnapSettings.defaults();
    private PresentedViewportFrame frame;
    private GameViewportLayout bounds;
    private List<AABB> sources=List.of();
    private Set<UUID> excluded=Set.of();
    private ViewportSnapGeometry geometry=ViewportSnapGeometry.EMPTY;
    private long token;
    ViewportMoveSnap(ViewportVisualization visibility){this.visibility=visibility;}
    void publisher(Consumer<ViewportSnapGeometry.Query> publisher){publish=publisher;}
    void settings(ViewportSnapSettings value){settings=value;token=TOKENS.incrementAndGet();geometry=ViewportSnapGeometry.EMPTY;publish.accept(null);}
    void begin(PresentedViewportFrame frame,GameViewportLayout bounds,Collection<SceneObject> objects){
        end();this.frame=frame;this.bounds=bounds;
        sources=objects.stream().map(ViewportSceneMath::bounds).toList();
        Set<UUID> ids=new HashSet<>();for(var object:objects)ids.add(object.id());excluded=Set.copyOf(ids);
    }
    void suspend(){publish.accept(null);}
    void end(){token=TOKENS.incrementAndGet();frame=null;sources=List.of();excluded=Set.of();geometry=ViewportSnapGeometry.EMPTY;publish.accept(null);}
    boolean presented(PresentedViewportFrame next){
        var candidate=next.snapGeometry();
        if(candidate.queryToken()!=token||candidate==geometry)return false;
        geometry=candidate;return true;
    }
    Vec3 apply(Vec3 origin,Vec3 movement,int axes,float x,float y){
        if(frame==null||!settings.enabled())return movement;
        double u=(x-bounds.left())/(bounds.right()-bounds.left()),v=(y-bounds.top())/(bounds.bottom()-bounds.top());
        double radiusU=UIUtils.dp2px(18)/(bounds.right()-bounds.left()),radiusV=UIUtils.dp2px(18)/(bounds.bottom()-bounds.top());
        if(settings.needsGeometry())publish.accept(ViewportSnapGeometry.Query.create(frame,token,origin.add(movement),u,v,radiusU,radiusV,excluded,visibility.snapshot(),settings.excludeUnselectable()));
        return ViewportSnapSolver.move(settings,origin,movement,axes,sources,frame,geometry,u,v,radiusU,radiusV);
    }
}
