package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSettings;
import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapGeometry;
import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.scene.SceneEditCapabilities;
import com.mine.geometry_node.client.runtime.game.selection.*;
import com.mine.geometry_node.client.ui.editor.game.selection.ViewportSelectionOverlay;
import com.mine.geometry_node.client.runtime.game.scene.ScenePresentations;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.client.runtime.game.scene.ViewportSceneMath;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.client.ui.editor.game.layout.GameViewportLayout;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Paint;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import java.util.*;

/** Scene gestures refer to the image actually presented, including throttled background views. */
public final class ViewportSceneInteraction {
    private static final EditViewportCamera.Axis[] AXES={EditViewportCamera.Axis.POSITIVE_X,EditViewportCamera.Axis.POSITIVE_Y,EditViewportCamera.Axis.POSITIVE_Z};
    private final ClientSceneService service;
    private final ViewportVisualization visibility;
    private final Runnable changed;
    private final Paint paint=new Paint();
    private PresentedViewportFrame frame,gestureFrame;
    private GameViewportLayout bounds,gestureBounds;
    private float startX,startY,currentX,currentY;
    private boolean pressed,additive,subtractive,marquee;
    private Set<ViewportTarget.Key> selectionAtPress=Set.of();
    private Set<ViewportTarget.Key> worldSelection=Set.of();
    private String selectionMessage="";
    private Set<UUID> expectedSceneSelection=Set.of();
    private long selectionGeneration=-1;
    private final ViewportWorldActions worldActions=new ViewportWorldActions();
    private final ViewportSelectionOverlay worldOverlay=new ViewportSelectionOverlay();
    private static ViewportTarget.Key sceneKey(UUID id){return new ViewportTarget.Key("geometry_node:scene",id.toString());}
    public void publishWorldSelection(Object owner, boolean enabled) {
        var snapshot = service.snapshot();
        boolean valid = enabled && selectionGeneration == snapshot.worldGeneration()
                && snapshot.selected().equals(expectedSceneSelection);
        com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime.setSelection(
                owner, snapshot.worldGeneration(), valid ? actionableWorldSelection() : Set.of());
    }
    private Set<ViewportTarget.Key> actionableWorldSelection() {
        if (!current(frame)) return Set.of();
        Set<ViewportTarget.Key> result = new LinkedHashSet<>();
        for (var target : frame.targets()) {
            UUID editable = ViewportSelectionSources.editableObject(target.key());
            if ((worldSelection.contains(target.key()) || (editable != null && service.snapshot().selected().contains(editable)))
                    && selectable(target)) result.add(target.key());
        }
        return Set.copyOf(result);
    }
    public Set<ViewportTarget.Key> selectedTargets(){return Set.copyOf(combinedSelection());}
    private ViewportTarget.Key selectionKey(ViewportTarget.Key key) {
        UUID editable = ViewportSelectionSources.editableObject(key);
        return editable != null && service.snapshot().object(editable) != null ? sceneKey(editable) : key;
    }
    private Set<ViewportTarget.Key> combinedSelection(){
        Set<ViewportTarget.Key> result=new LinkedHashSet<>(worldSelection);
        for(UUID id:service.snapshot().selected())result.add(sceneKey(id));
        return result;
    }
    private void applySelection(Set<ViewportTarget.Key> selected){applySelection(selected,null);}
    private void applySelection(Set<ViewportTarget.Key> selected,ViewportTarget.Key preferred){
        selectionMessage="";
        Set<UUID> scene=new LinkedHashSet<>();Set<ViewportTarget.Key> world=new LinkedHashSet<>();
        for (var key : selected) {
            UUID editable = key.source().equals("geometry_node:scene") ? UUID.fromString(key.id())
                    : ViewportSelectionSources.editableObject(key);
            if (editable != null && service.snapshot().object(editable) != null) scene.add(editable);
            else world.add(key);
        }
        if(!worldSelection.equals(world))worldActions.clear();
        worldSelection=Set.copyOf(world);expectedSceneSelection=Set.copyOf(scene);
        UUID active=preferred!=null&&preferred.source().equals("geometry_node:scene")?UUID.fromString(preferred.id()):service.snapshot().active();
        if (preferred != null && !preferred.source().equals("geometry_node:scene")) {
            UUID editable = ViewportSelectionSources.editableObject(preferred);
            if (editable != null && scene.contains(editable)) active = editable;
        }
        if(active==null||!scene.contains(active))active=scene.isEmpty()?null:scene.iterator().next();
        service.select(scene,active);
    }
    public boolean contextAction(float x,float y){
        if(busy()||!current(frame)||bounds==null||!bounds.contains(x,y))return false;
        var selected=actionableWorldSelection();
        if(selected.isEmpty())return false;
        boolean result=ViewportSelectionSources.action(selected,frame,ViewportSceneMath.ray(frame,u(bounds,x),v(bounds,y)),worldActions);
        if(result)changed.run();return result;
    }
    private EditViewportCamera.Axis dragAxis;
    private Vec3 dragOrigin;
    private Map<UUID,Vec3> originals=Map.of();
    private UUID hovered;
    private ViewportTransformGesture transform;
    private final ViewportMoveSnap snapping;
    private ViewportSnapSettings snapSettings=ViewportSnapSettings.defaults();
    public void setSnapQueryPublisher(java.util.function.Consumer<ViewportSnapGeometry.Query> publisher){snapping.publisher(publisher);}
    public void setSnap(boolean enabled){setSnapSettings(snapSettings.withEnabled(enabled));}
    public void setSnapSettings(ViewportSnapSettings settings){
        snapSettings=settings;snapping.settings(settings);
        if(transforming())transform.refresh();else if(dragAxis!=null)move(currentX,currentY);
        changed.run();
    }
    public String transformLabel(){return transforming()?transform.label():!selectionMessage.isEmpty()?selectionMessage:worldActions.message();}
    public boolean transforming(){if(transform!=null&&!transform.active()){transform=null;snapping.end();}return transform!=null;}
    public void transformAxis(int bit){if(transforming()){transform.axis(bit);changed.run();}}
    public void confirmTransform(){if(transforming()&&transform.confirm()){transform=null;snapping.end();}changed.run();}
    public boolean transformNumberKey(int key){if(!transforming()||!transform.numberKey(key))return false;changed.run();return true;}
    public void transformMove(float x,float y){if(transforming()){transform.move(x,y);changed.run();}}
    public void beginTransform(String operation,float x,float y){
        cancel();if(!worldSelection.isEmpty()||!current(frame)||bounds==null)return;
        if(!Float.isFinite(x)||!Float.isFinite(y)||!bounds.contains(x,y)){x=(bounds.left()+bounds.right())/2;y=(bounds.top()+bounds.bottom())/2;}
        var snapshot=service.snapshot();Map<UUID,SceneObject> objects=new LinkedHashMap<>();
        for(var o:frame.scene().objects())if(snapshot.selected().contains(o.id())&&selectable(o))objects.put(o.id(),o);
        if(objects.isEmpty()||objects.size()!=snapshot.selected().size())return;
        var availability = SceneEditCapabilities.evaluate(SceneEditCapabilities.Action.valueOf(operation), objects.values(), false);
        if (!availability.allowed()) { selectionMessage = availability.message(); changed.run(); return; }
        var gesture=new ViewportTransformGesture(service,frame,bounds,objects,ViewportTransformGesture.Operation.valueOf(operation),x,y,snapping);
        if(gesture.begin()){transform=gesture;snapping.begin(frame,bounds,objects.values());transform.refresh();}changed.run();
    }
    public ViewportSceneInteraction(ViewportVisualization visibility,Runnable changed){this(ClientSceneService.INSTANCE,visibility,changed);}
    ViewportSceneInteraction(ClientSceneService service,ViewportVisualization visibility,Runnable changed){this.service=service;this.visibility=visibility;this.changed=changed;snapping=new ViewportMoveSnap(visibility);paint.setAntiAlias(true);}
    public void presented(PresentedViewportFrame frame,GameViewportLayout bounds){
        this.frame=frame;this.bounds=bounds;
        if(snapping.presented(frame)){
            if(transforming())transform.refresh();else if(dragAxis!=null)move(currentX,currentY);
        }
        var snapshot=service.snapshot();
        if(selectionGeneration!=snapshot.worldGeneration()||!snapshot.selected().equals(expectedSceneSelection)){
            worldSelection=Set.of();worldActions.clear();selectionMessage="";expectedSceneSelection=snapshot.selected();
            selectionGeneration=snapshot.worldGeneration();
        }
        // A bounded server query can arrive after the first selectable diagnostic frame.
        if (worldSelection.stream().anyMatch(key -> {
            UUID id = ViewportSelectionSources.editableObject(key);
            return id != null && snapshot.object(id) != null;
        })) applySelection(combinedSelection());
    }
    public void clearFrame(){frame=null;bounds=null;cancel();}
    public boolean busy(){return pressed||transforming();}
    public void cancel(){snapping.end();if(transform!=null){transform.cancel();transform=null;changed.run();}boolean wasPressed=pressed||hovered!=null;hovered=null;if(dragAxis!=null)service.cancelPreview(this);pressed=marquee=false;dragAxis=null;gestureFrame=null;originals=Map.of();if(wasPressed)changed.run();}
    public boolean down(float x,float y,boolean shift){return down(x,y,shift,false);}
    public boolean down(float x,float y,boolean shift,boolean control){
        if(!current(frame)||bounds==null||!bounds.contains(x,y))return false;
        gestureFrame=frame;gestureBounds=bounds;startX=currentX=x;startY=currentY=y;subtractive=control;additive=shift&&!control;pressed=true;selectionAtPress=combinedSelection();
        dragAxis=shift||control?null:hitAxis(x,y);
        if(dragAxis!=null){
            var snapshot=service.snapshot();
            if(!snapshot.canEdit()||snapshot.pending()){dragAxis=null;pressed=false;return true;}
            Map<UUID, SceneObject> objects = new LinkedHashMap<>();
            Map<UUID, Vec3> positions = new LinkedHashMap<>();
            Map<UUID, Long> revisions = new LinkedHashMap<>();
            for (var object : frame.scene().objects()) {
                if (!snapshot.selected().contains(object.id()) || !selectable(object)) continue;
                objects.put(object.id(), object);
                positions.put(object.id(), object.position());
                revisions.put(object.id(), object.revision());
            }
            if (objects.isEmpty() || objects.size() != snapshot.selected().size()
                    || !service.beginPreview(this, frame.scene().worldGeneration(), revisions, objects)) {
                dragAxis = null;
                pressed = false;
                return true;
            }
            originals = Map.copyOf(positions);
            dragOrigin = center(positions.values());
            snapping.begin(frame, bounds, objects.values());
        }
        return true;
    }
    public boolean move(float x,float y){
        if(!pressed)return false;if(!current(gestureFrame)){cancel();return true;}currentX=x;currentY=y;
        if(dragAxis!=null){
            if(!service.previewActive(this)){snapping.end();pressed=false;dragAxis=null;gestureFrame=null;originals=Map.of();changed.run();return true;}
            double amount=ViewportSceneMath.axisDrag(gestureFrame,dragOrigin,dragAxis,u(gestureBounds,startX),v(gestureBounds,startY),u(gestureBounds,x),v(gestureBounds,y));
            if(Double.isFinite(amount)){
                Vec3 movement=new Vec3(dragAxis.x*amount,dragAxis.y*amount,dragAxis.z*amount);
                int mask=dragAxis.x!=0?1:dragAxis.y!=0?2:4;
                Vec3 delta=snapping.apply(dragOrigin,movement,mask,x,y);
                Map<UUID,Vec3> next=new LinkedHashMap<>();originals.forEach((id,p)->next.put(id,p.add(delta)));try { service.previewPositions(this,next); } catch (IllegalArgumentException invalid) { cancel(); }
            }
        }else if(Math.hypot(x-startX,y-startY)>UIUtils.dp2px(4))marquee=true;
        changed.run();return true;
    }
    public boolean up(float x,float y){
        if(!pressed)return false;if(!current(gestureFrame)){cancel();return true;}move(x,y);
        if(!pressed)return true;
        if(dragAxis!=null){service.commitPreview(this);snapping.end();}
        else {
            Set<ViewportTarget.Key> hits=new LinkedHashSet<>();
            if(marquee){
                double minU=Math.min(u(gestureBounds,startX),u(gestureBounds,x)),maxU=Math.max(u(gestureBounds,startX),u(gestureBounds,x));
                double minV=Math.min(v(gestureBounds,startY),v(gestureBounds,y)),maxV=Math.max(v(gestureBounds,startY),v(gestureBounds,y));
                for(var object:gestureFrame.scene().objects())if(selectable(object,gestureFrame)&&ViewportSceneMath.intersects(gestureFrame,selectionBounds(object,gestureFrame),minU,minV,maxU,maxV))hits.add(sceneKey(object.id()));
                for(var target:gestureFrame.targets())if(selectable(target)&&ViewportSceneMath.intersects(gestureFrame,target.bounds(),minU,minV,maxU,maxV))hits.add(selectionKey(target.key()));
            }else{
                UUID object=pick(gestureFrame,gestureBounds,x,y);
                double depth=Double.POSITIVE_INFINITY;
                ViewportTarget.Key hit=object==null?null:sceneKey(object);
                if(object!=null)for(var o:gestureFrame.scene().objects())if(o.id().equals(object))
                    depth=ScenePresentations.pointMarker(o)?lightDepth(gestureFrame,o):ViewportSceneMath.pickBounds(gestureFrame,u(gestureBounds,x),v(gestureBounds,y),selectionBounds(o,gestureFrame));
                for(var target:gestureFrame.targets()){
                    if(!selectable(target))continue;
                    double d=ViewportSceneMath.pickBounds(gestureFrame,u(gestureBounds,x),v(gestureBounds,y),target.bounds());
                    if(d<depth){depth=d;hit=selectionKey(target.key());}
                }
                if(hit!=null)hits.add(hit);
            }
            applySelection(ViewportSelection.apply(selectionAtPress,hits,additive,subtractive,!marquee),
                    !marquee&&!hits.isEmpty()?hits.iterator().next():null);
            if(hits.isEmpty()&&!additive&&!subtractive)worldActions.clear();
        }
        pressed=marquee=false;dragAxis=null;gestureFrame=null;originals=Map.of();changed.run();return true;
    }
    public void hover(float x,float y){UUID next=frame==null?null:pick(frame,bounds,x,y);if(!Objects.equals(next,hovered)){hovered=next;changed.run();}}
    private UUID pick(PresentedViewportFrame f,GameViewportLayout b,float x,float y){
        if(!current(f)||!b.contains(x,y))return null;
        UUID best=null;double depth=Double.POSITIVE_INFINITY;
        for(var object:f.scene().objects()){
            if(!selectable(object,f))continue;
            double hit=ViewportSceneMath.pickBounds(f,u(b,x),v(b,y),selectionBounds(object,f));
            if(!ScenePresentations.pointMarker(object)){
                if(hit<depth){depth=hit;best=object.id();}continue;
            }
            var p=ViewportSceneMath.project(f,object.position());
            if(!p.visible())continue;
            double dx=px(b,p.u())-x,dy=py(b,p.v())-y;
            if(dx*dx+dy*dy<=Math.pow(UIUtils.dp2px(11),2)&&lightDepth(f,object)<depth){depth=lightDepth(f,object);best=object.id();}
        }return best;
    }
    public void copySelected() {
        if (prepareSceneClipboardOperation(SceneEditCapabilities.Action.COPY)) service.copySelected();
        changed.run();
    }
    public void paste() {
        cancel(); selectionMessage="";
        service.paste(); changed.run();
    }
    public void deleteSelected() {
        if (prepareSceneClipboardOperation(SceneEditCapabilities.Action.DELETE)) service.deleteSelected();
        changed.run();
    }
    private boolean prepareSceneClipboardOperation(SceneEditCapabilities.Action action) {
        cancel();
        var snapshot=service.snapshot();
        boolean unresolvedTargets=selectionGeneration==snapshot.worldGeneration()
                && snapshot.selected().equals(expectedSceneSelection) && !worldSelection.isEmpty();
        var availability = service.availability(action, unresolvedTargets);
        selectionMessage = availability.message();
        return availability.allowed();
    }
    public void selectAll() {
        cancel();
        if (!current(frame)) return;
        Set<ViewportTarget.Key> ids = new LinkedHashSet<>();
        for (var object : frame.scene().objects()) if (selectable(object)) ids.add(sceneKey(object.id()));
        for (var target : frame.targets()) if (selectable(target)) ids.add(target.key());
        applySelection(ids);
    }
    public void focus(EditViewportCamera camera){
        var s=service.snapshot();AABB box=null;
        if(current(frame))for(var o:frame.scene().objects())if(s.selected().contains(o.id())&&selectable(o)){
            AABB b=ViewportSelectionSources.presentedBounds(o,frame);if(b==null)b=ViewportSceneMath.focusBounds(o);box=box==null?b:box.minmax(b);
        }
        if(current(frame))for(var target:frame.targets())if(selectable(target)&&worldSelection.contains(target.key()))box=box==null?target.bounds():box.minmax(target.bounds());
        if(box!=null){cancel();camera.focus(box,frame==null?1:frame.aspect(),1.2);changed.run();}
    }
    public com.mine.geometry_node.client.runtime.game.scene.ViewportCreationPlacement.Request creationPosition(float x, float y) {
        if (!current(frame)) return null;
        boolean inside = bounds != null && bounds.contains(x, y);
        return com.mine.geometry_node.client.runtime.game.scene.ViewportCreationPlacement.capture(
                frame, inside ? u(bounds, x) : .5, inside ? v(bounds, y) : .5);
    }
    public void draw(Canvas canvas){
        if(!current(frame)||bounds==null)return;
        canvas.save();canvas.clipRect(bounds.left(),bounds.top(),bounds.right(),bounds.bottom());
        {
            var selection=service.snapshot().selected();
            for(var object:frame.scene().objects()){
                var layer=ScenePresentations.layer(object);
                if(layer==null||!visibility.visible(layer))continue;
                if(layer==ViewportVisualization.Layer.INTERACTION&&!visibility.helpers())continue;
                if(!ViewportSelectionSources.presented(object,frame))continue;
                if(!ScenePresentations.pointMarker(object)){
                    if(selection.contains(object.id())||Objects.equals(hovered,object.id()))drawBounds(canvas,object,selection.contains(object.id()));
                    continue;
                }
                var projected=ViewportSceneMath.project(frame,object.position());if(!projected.visible())continue;
                boolean selected=selection.contains(object.id());boolean active=object.id().equals(service.snapshot().active());
                float x=px(bounds,projected.u()),y=py(bounds,projected.v()),r=UIUtils.dp2px(selected?7:5);
                paint.setStyle(Paint.STROKE);paint.setStrokeWidth(UIUtils.dp2px(selected?2:1.3f));
                paint.setColor(selected?(active?0xFFFFD98A:0xFFFF9E35):Objects.equals(hovered,object.id())?0xFFFFFFFF:0xFFE4D899);
                canvas.drawCircle(x,y,r,paint);canvas.drawLine(x-r*1.7f,y,x+r*1.7f,y,paint);canvas.drawLine(x,y-r*1.7f,x,y+r*1.7f,paint);
                if(active&&selected&&visibility.helpers())drawRange(canvas,object);
            }
            if(visibility.gizmo())drawAxes(canvas);
        }
        worldOverlay.draw(canvas,frame,bounds,actionableWorldSelection(),worldActions.target(),this::selectable);
        if(pressed&&marquee){
            paint.setStyle(Paint.FILL);paint.setColor(0x224C9AFF);canvas.drawRect(Math.min(startX,currentX),Math.min(startY,currentY),Math.max(startX,currentX),Math.max(startY,currentY),paint);
            paint.setStyle(Paint.STROKE);paint.setStrokeWidth(UIUtils.dp2px(1));paint.setColor(0xFF75AFFF);canvas.drawRect(Math.min(startX,currentX),Math.min(startY,currentY),Math.max(startX,currentX),Math.max(startY,currentY),paint);
        }
        paint.setStyle(Paint.FILL);canvas.restore();
    }
    private boolean selectable(SceneObject object){
        return selectable(object,frame);
    }
    private boolean selectable(SceneObject object, PresentedViewportFrame presented){
        var layer=ScenePresentations.layer(object);
        return layer!=null&&visibility.selectable(layer)
                && (layer != ViewportVisualization.Layer.INTERACTION || visibility.helpers())
                && ViewportSelectionSources.presented(object,presented);
    }
    private boolean selectable(ViewportTarget target){
        return ViewportSelectionSources.selectable(target.key(),visibility.snapshot());
    }
    private static double lightDepth(PresentedViewportFrame f,SceneObject o){
        var ray=ViewportSceneMath.ray(f,.5,.5);
        return ray==null?Double.POSITIVE_INFINITY:o.position().subtract(ray.origin()).dot(ray.direction());
    }
    private void drawBounds(Canvas canvas,SceneObject object,boolean selected){
        AABB box=selectionBounds(object,frame);
        paint.setColor(selected?0xFFFFB547:0xFFFFFFFF);paint.setStrokeWidth(UIUtils.dp2px(selected?2:1));
        for(int i=0;i<8;i++)for(int bit:new int[]{1,2,4})if((i&bit)==0){
            int j=i|bit;
            var a=ViewportSceneMath.project(frame,new Vec3((i&1)==0?box.minX:box.maxX,(i&2)==0?box.minY:box.maxY,(i&4)==0?box.minZ:box.maxZ));
            var b=ViewportSceneMath.project(frame,new Vec3((j&1)==0?box.minX:box.maxX,(j&2)==0?box.minY:box.maxY,(j&4)==0?box.minZ:box.maxZ));
            if(a.visible()&&b.visible())canvas.drawLine(px(bounds,a.u()),py(bounds,a.v()),px(bounds,b.u()),py(bounds,b.v()),paint);
        }
    }
    private void drawRange(Canvas canvas,SceneObject object){
        paint.setStyle(Paint.STROKE);paint.setStrokeWidth(UIUtils.dp2px(1));paint.setColor(0x66FFB547);
        for(int plane=0;plane<3;plane++)for(int i=0;i<48;i++){
            double a=i*Math.PI/24,b=(i+1)*Math.PI/24,r=0.5;
            Vec3 p=com.mine.geometry_node.core.engine.system.scene.SceneTransforms.local(object,ring(plane,Math.cos(a)*r,Math.sin(a)*r)).add(object.position()),q=com.mine.geometry_node.core.engine.system.scene.SceneTransforms.local(object,ring(plane,Math.cos(b)*r,Math.sin(b)*r)).add(object.position());
            var from=ViewportSceneMath.project(frame,p);var to=ViewportSceneMath.project(frame,q);
            if(from.visible()&&to.visible())canvas.drawLine(px(bounds,from.u()),py(bounds,from.v()),px(bounds,to.u()),py(bounds,to.v()),paint);
        }
    }
    private static Vec3 ring(int plane,double a,double b){return plane==0?new Vec3(a,b,0):plane==1?new Vec3(a,0,b):new Vec3(0,a,b);}
    private Vec3 selectedCenter(){
        if(!worldSelection.isEmpty())return null;
        List<Vec3> points=new ArrayList<>();List<SceneObject> objects=new ArrayList<>();var selected=service.snapshot().selected();
        for(var o:frame.scene().objects())if(selected.contains(o.id())&&selectable(o)) {
            objects.add(o);
            points.add(o.position());
        }
        return points.size()!=selected.size()||!SceneEditCapabilities.evaluate(SceneEditCapabilities.Action.MOVE,objects,false).allowed()?null:center(points);
    }
    private double axisLength(Vec3 origin){
        var c=frame.camera();double pixels=Math.max(1,bounds.bottom()-bounds.top());
        double height=c.orthographic()?c.orthographicHeight():2*Math.max(.1,new Vec3(c.x(),c.y(),c.z()).distanceTo(origin))*Math.tan(Math.toRadians(c.verticalFovDegrees())/2);
        return height*UIUtils.dp2px(72)/pixels;
    }
    private void drawAxes(Canvas canvas){
        Vec3 center=selectedCenter();if(center==null)return;var p=ViewportSceneMath.project(frame,center);if(!p.visible())return;
        float x=px(bounds,p.u()),y=py(bounds,p.v());double length=axisLength(center);
        for(int i=0;i<3;i++){var axis=AXES[i];var end=ViewportSceneMath.project(frame,center.add(axis.x*length,axis.y*length,axis.z*length));if(!end.visible())continue;
            paint.setColor((dragAxis==axis||(transforming()&&(transform.axes()&(1<<i))!=0))?0xFFFFFF77:new int[]{0xFFEB5967,0xFFA4CE50,0xFF4698EE}[i]);paint.setStrokeWidth(UIUtils.dp2px(2));
            float ex=px(bounds,end.u()),ey=py(bounds,end.v());canvas.drawLine(x,y,ex,ey,paint);paint.setStyle(Paint.FILL);canvas.drawCircle(ex,ey,UIUtils.dp2px(7),paint);drawAxisLetter(canvas,axis,ex,ey);
        }
    }
    private void drawAxisLetter(Canvas canvas,EditViewportCamera.Axis axis,float x,float y){
        float unit=UIUtils.dp2px(1),w=2.3f*unit,h=3.1f*unit;
        paint.setColor(0xFF182029);paint.setStrokeWidth(1.2f*unit);
        if(axis.x!=0){canvas.drawLine(x-w,y-h,x+w,y+h,paint);canvas.drawLine(x+w,y-h,x-w,y+h,paint);}
        else if(axis.y!=0){canvas.drawLine(x-w,y-h,x,y,paint);canvas.drawLine(x+w,y-h,x,y,paint);canvas.drawLine(x,y,x,y+h,paint);}
        else{canvas.drawLine(x-w,y-h,x+w,y-h,paint);canvas.drawLine(x+w,y-h,x-w,y+h,paint);canvas.drawLine(x-w,y+h,x+w,y+h,paint);}
    }
    private EditViewportCamera.Axis hitAxis(float x,float y){
        if(!visibility.gizmo())return null;
        Vec3 origin=selectedCenter();if(origin==null)return null;var p=ViewportSceneMath.project(frame,origin);if(!p.visible())return null;
        double length=axisLength(origin),best=UIUtils.dp2px(7);EditViewportCamera.Axis hit=null;
        for(var axis:AXES){var q=ViewportSceneMath.project(frame,origin.add(axis.x*length,axis.y*length,axis.z*length));if(!q.visible())continue;
            double ax=px(bounds,p.u()),ay=py(bounds,p.v()),dx=px(bounds,q.u())-ax,dy=py(bounds,q.v())-ay,len=dx*dx+dy*dy;if(len<64)continue;
            double t=Math.clamp(((x-ax)*dx+(y-ay)*dy)/len,.2,1);double distance=Math.hypot(x-ax-t*dx,y-ay-t*dy);if(distance<best){best=distance;hit=axis;}}
        return hit;
    }
    private boolean current(PresentedViewportFrame frame){return frame!=null&&frame.scene().worldGeneration()==service.snapshot().worldGeneration();}
    private static AABB selectionBounds(SceneObject object,PresentedViewportFrame frame){
        AABB presented=ViewportSelectionSources.presentedBounds(object,frame);
        return presented==null?ViewportSceneMath.bounds(object):presented;
    }
    private static Vec3 center(Collection<Vec3> points){Vec3 sum=Vec3.ZERO;for(var p:points)sum=sum.add(p);return sum.scale(1.0/points.size());}
    private static double u(GameViewportLayout b,float x){return (x-b.left())/(b.right()-b.left());}
    private static double v(GameViewportLayout b,float y){return (y-b.top())/(b.bottom()-b.top());}
    private static float px(GameViewportLayout b,double u){return b.left()+(float)u*(b.right()-b.left());}
    private static float py(GameViewportLayout b,double v){return b.top()+(float)v*(b.bottom()-b.top());}
}
