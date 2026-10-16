package com.mine.geometry_node.client.ui.editor.game.sidebar;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes;
import com.mine.geometry_node.client.ui.editor.game.GameViewportWindow;
import com.mine.geometry_node.client.ui.editor.game.ViewportMode;
import com.mine.geometry_node.client.ui.components.sidebar.api.*;
import com.mine.geometry_node.client.ui.components.common.*;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Explicitly committed property edits preserve one coherent transaction and wait for server acknowledgement. */
public final class ScenePropertiesPanel extends ScrollView implements SidebarPanel {
    public static final String PANEL_ID = "scene_properties";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(PANEL_ID,
            "geometry_node.game_view.scene.properties", 90, Set.of(SidebarPanelScope.GAME_VIEWPORT),
            c -> new ScenePropertiesPanel(c.uiContext(),c.requireOwner(GameViewportWindow.class)),SvgIconView.Icon.PROPERTIES);
    private final ClientSceneService service;
    private final java.util.function.BooleanSupplier editingMode;
    private final Map<String,UiTextInput> fields = new LinkedHashMap<>();
    private final LinearLayout form;
    private final LinearLayout parameterForm;
    private final Map<String,UiTextInput> parameterFields=new LinkedHashMap<>();
    private String formType="";
    private final TextView status;
    private final UiCheckBox enabled;
    private SceneObject editing, submitted;
    private boolean updatingFields, dirty;
    private final Runnable autoCommit=this::commitDraft;
    private void watch(UiTextInput input){
        input.addTextChangedListener(new icyllis.modernui.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence text,int start,int before,int count){}
            @Override public void afterTextChanged(icyllis.modernui.text.Editable text){markDirty();}
        });
        input.setOnKeyListener((view,key,event)->{
            if(key!=icyllis.modernui.view.KeyEvent.KEY_ENTER && key!=icyllis.modernui.view.KeyEvent.KEY_KP_ENTER)return false;
            if(event.getAction()==icyllis.modernui.view.KeyEvent.ACTION_DOWN && event.getRepeatCount()==0)commitDraft();
            return true;
        });
        input.setOnFocusChangeListener((view,focused)->{if(!focused&&dirty&&!updatingFields)commitDraft();});
    }
    private void markDirty(){
        if(updatingFields||editing==null)return;
        dirty=true;validationMessage="";removeCallbacks(autoCommit);
    }
    /** Parent invokes before dispatch, including clicks on non-focusable blank space. */
    public void commitIfOutside(float rootX,float rootY){
        if(!(findFocus() instanceof UiTextInput input))return;
        float[] point=new float[2];
        if(!UIUtils.rootToLocal(input,rootX,rootY,new icyllis.modernui.graphics.Matrix(),point)
                ||point[0]<0||point[1]<0||point[0]>=input.getWidth()||point[1]>=input.getHeight()){
            commitDraft();input.clearFocus();
        }
    }
    private void discardDraft(){removeCallbacks(autoCommit);dirty=false;submitted=null;}
    @Override protected void onDetachedFromWindow(){discardDraft();editing=null;super.onDetachedFromWindow();}

    private long editingGeneration;
    private String validationMessage="";
    public ScenePropertiesPanel(Context context,GameViewportWindow owner) {
        this(context,ClientSceneService.INSTANCE,()->owner.mode()==ViewportMode.EDIT);
    }
    ScenePropertiesPanel(Context context,ClientSceneService service,java.util.function.BooleanSupplier editingMode){
        super(context);this.service=service;this.editingMode=editingMode;
        LinearLayout column=new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
        int p=UIUtils.dp2pxInt(10); column.setPadding(p,p,p,p);
        addView(column,new ViewGroup.LayoutParams(-1,-2));
        status=UIUtils.createLockedTextView(context,"",11,UiTheme.Text.MUTED);
        column.addView(status,new LinearLayout.LayoutParams(-1,-2));
        form=new LinearLayout(context); form.setOrientation(LinearLayout.VERTICAL);
        column.addView(form,new LinearLayout.LayoutParams(-1,-2));
        for(String key:List.of("name","x","y","z","rx","ry","rz","sx","sy","sz","red","green","blue")) {
            LinearLayout row=new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label=UIUtils.createLockedTextView(context,tr(key),11,UiTheme.Text.SECONDARY);
            row.addView(label,new LinearLayout.LayoutParams(UIUtils.dp2pxInt(66),UIUtils.dp2pxInt(28)));
            UiTextInput input=new UiTextInput(context); input.setSingleLine(true); fields.put(key,input);watch(input);
            row.addView(input,new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=UIUtils.dp2pxInt(5);
            form.addView(row,lp);
        }
        parameterForm=new LinearLayout(context);parameterForm.setOrientation(LinearLayout.VERTICAL);
        form.addView(parameterForm,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout toggle=new LinearLayout(context);toggle.setOrientation(LinearLayout.HORIZONTAL);
        enabled=new UiCheckBox(context); toggle.addView(enabled,new LinearLayout.LayoutParams(UIUtils.dp2pxInt(20),UIUtils.dp2pxInt(20)));
        toggle.addView(UIUtils.createLockedTextView(context,tr("enabled"),11,UiTheme.Text.NORMAL));
        form.addView(toggle,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(30)));
        enabled.setOnCheckedChangeListener((view,checked)->{markDirty();commitDraft();});
        refresh();
    }
    @Override public View getView(){return this;}
    @Override public void onSelected(){refresh();}
    public void refresh(){
        var snapshot=service.confirmedSnapshot();
        SceneObject object=snapshot.active()==null?null:snapshot.object(snapshot.active());
        form.setVisibility(object==null?GONE:VISIBLE);
        status.setText(!validationMessage.isBlank()?validationMessage:!snapshot.message().isBlank()?snapshot.message():object==null?tr("nothing_selected"):
                Component.translatable("geometry_node.game_view.scene.selected",snapshot.selected().size()).getString());
        boolean editable=editingMode.getAsBoolean()&&snapshot.canEdit();
        enabled.setEnabled(editable);
        for(var input:fields.values())input.setEnabled(editable);
        for(var input:parameterFields.values())input.setEnabled(editable);
        if(!editable){if(dirty)editing=null;discardDraft();}
        if(object==null){discardDraft();editing=null;return;}
        boolean sameTarget=editing!=null&&editingGeneration==snapshot.worldGeneration()&&object.id().equals(editing.id());
        if(sameTarget&&submitted!=null){
            if(submitted.sameContent(object)){editing=object;if(!snapshot.pending())submitted=null;}
            else if(!snapshot.pending()){discardDraft();editing=null;}
        }

        if(editing==null||editingGeneration!=snapshot.worldGeneration()||!object.id().equals(editing.id())||object.revision()!=editing.revision()){
            discardDraft();updatingFields=true;
            try {
            rebuildParameters(object);
            editing=object;editingGeneration=snapshot.worldGeneration();validationMessage="";
            fields.get("name").setText(object.name());
            double[] values={object.position().x,object.position().y,object.position().z,Math.toDegrees(object.rotation().x),Math.toDegrees(object.rotation().y),Math.toDegrees(object.rotation().z),object.scale().x,object.scale().y,object.scale().z,object.color().x,object.color().y,object.color().z};
            int i=0;for(String key:List.of("x","y","z","rx","ry","rz","sx","sy","sz","red","green","blue"))fields.get(key).setText(Double.toString(values[i++]));
            for(var field:parameterFields.entrySet()){field.getValue().setText(object.parameters().get(field.getKey()));field.getValue().setEnabled(editable);}
            enabled.setChecked(object.enabled());
            } finally {updatingFields=false;}
        }
    }
    private void commitDraft(){
        if(!dirty)return;
        refresh();
        if(!dirty)return;
        var snapshot=service.confirmedSnapshot();
        if(editing==null||editingGeneration!=snapshot.worldGeneration()||!editingMode.getAsBoolean()||!snapshot.canEdit())return;
        if(snapshot.pending()){removeCallbacks(autoCommit);postDelayed(autoCommit,100);return;}
        try{
            validationMessage="";
            Vec3 position=new Vec3(value("x"),value("y"),value("z"));
            Vec3 color=new Vec3(value("red"),value("green"),value("blue"));
            Map<String,String> parameters=new LinkedHashMap<>();
            parameterFields.forEach((key,input)->parameters.put(key,input.getText().toString()));
            SceneObject replacement=editing.withTransform(position,new Vec3(Math.toRadians(value("rx")),Math.toRadians(value("ry")),Math.toRadians(value("rz"))),new Vec3(value("sx"),value("sy"),value("sz"))).withProperties(fields.get("name").getText().toString(),color,enabled.isChecked(),parameters);
            SceneObjectTypes.validate(replacement);
            if(replacement.sameContent(editing)){dirty=false;return;}
            service.update(editingGeneration,replacement);
            if(service.snapshot().pending()){submitted=replacement;dirty=false;}

        }catch(RuntimeException invalid){validationMessage=tr("invalid");status.setText(validationMessage);}
    }
    private void rebuildParameters(SceneObject object){
        if(formType.equals(object.typeId()))return;
        formType=object.typeId();parameterFields.clear();parameterForm.removeAllViews();
        for(var property:SceneObjectTypes.require(formType).properties()){
            LinearLayout row=new LinearLayout(getContext());row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(UIUtils.createLockedTextView(getContext(),Component.translatable(property.labelKey()).getString(),11,UiTheme.Text.SECONDARY),
                    new LinearLayout.LayoutParams(UIUtils.dp2pxInt(66),UIUtils.dp2pxInt(28)));
            UiTextInput input=new UiTextInput(getContext());input.setSingleLine(true);parameterFields.put(property.key(),input);watch(input);
            row.addView(input,new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=UIUtils.dp2pxInt(5);parameterForm.addView(row,lp);
        }
    }
    private double value(String key){double value=Double.parseDouble(fields.get(key).getText().toString());if(!Double.isFinite(value))throw new IllegalArgumentException();return value;}
    private static String tr(String key){return Component.translatable("geometry_node.game_view.scene."+key).getString();}
}
