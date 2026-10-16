package com.mine.geometry_node.client.ui.editor.game.sidebar;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
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

/** Explicit Apply preserves a coherent property transaction and server revision. */
public final class ScenePropertiesPanel extends ScrollView implements SidebarPanel {
    public static final String PANEL_ID = "scene_properties";
    public static final SidebarPanelDefinition DEFINITION = new SidebarPanelDefinition(PANEL_ID,
            "geometry_node.game_view.scene.properties", 90, Set.of(SidebarPanelScope.GAME_VIEWPORT),
            c -> new ScenePropertiesPanel(c.uiContext(),c.requireOwner(GameViewportWindow.class)),SvgIconView.Icon.PROPERTIES);
    private final GameViewportWindow owner;
    private final Map<String,UiTextInput> fields = new LinkedHashMap<>();
    private final LinearLayout form;
    private final TextView status, apply;
    private final UiCheckBox enabled;
    private SceneObject editing;
    private long editingGeneration;
    private String validationMessage="";
    public ScenePropertiesPanel(Context context,GameViewportWindow owner) {
        super(context); this.owner=owner;
        LinearLayout column=new LinearLayout(context); column.setOrientation(LinearLayout.VERTICAL);
        int p=UIUtils.dp2pxInt(10); column.setPadding(p,p,p,p);
        addView(column,new ViewGroup.LayoutParams(-1,-2));
        status=UIUtils.createLockedTextView(context,"",11,UiTheme.Text.MUTED);
        column.addView(status,new LinearLayout.LayoutParams(-1,-2));
        form=new LinearLayout(context); form.setOrientation(LinearLayout.VERTICAL);
        column.addView(form,new LinearLayout.LayoutParams(-1,-2));
        for(String key:List.of("name","x","y","z","red","green","blue","intensity","range")) {
            LinearLayout row=new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL);
            TextView label=UIUtils.createLockedTextView(context,tr(key),11,UiTheme.Text.SECONDARY);
            row.addView(label,new LinearLayout.LayoutParams(UIUtils.dp2pxInt(66),UIUtils.dp2pxInt(28)));
            UiTextInput input=new UiTextInput(context); input.setSingleLine(true); fields.put(key,input);
            row.addView(input,new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=UIUtils.dp2pxInt(5);
            form.addView(row,lp);
        }
        LinearLayout toggle=new LinearLayout(context);toggle.setOrientation(LinearLayout.HORIZONTAL);
        enabled=new UiCheckBox(context); toggle.addView(enabled,new LinearLayout.LayoutParams(UIUtils.dp2pxInt(20),UIUtils.dp2pxInt(20)));
        toggle.addView(UIUtils.createLockedTextView(context,tr("enabled"),11,UiTheme.Text.NORMAL));
        form.addView(toggle,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(30)));
        apply=UIUtils.createLockedTextView(context,tr("apply"),12,UiTheme.Control.FOCUS);
        apply.setClickable(true);apply.setFocusable(true);apply.setFocusableInTouchMode(true);apply.setOnClickListener(v->apply());
        form.addView(apply,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(30)));
        refresh();
    }
    @Override public View getView(){return this;}
    @Override public void onSelected(){refresh();}
    public void refresh(){
        var snapshot=ClientSceneService.INSTANCE.snapshot();
        SceneObject object=snapshot.active()==null?null:snapshot.object(snapshot.active());
        form.setVisibility(object==null?GONE:VISIBLE);
        status.setText(!validationMessage.isBlank()?validationMessage:!snapshot.message().isBlank()?snapshot.message():object==null?tr("nothing_selected"):
                Component.translatable("geometry_node.game_view.scene.selected",snapshot.selected().size()).getString());
        boolean editable=owner.mode()==ViewportMode.EDIT&&snapshot.canEdit()&&!snapshot.pending();
        apply.setEnabled(editable);enabled.setEnabled(editable);
        for(var input:fields.values())input.setEnabled(editable);
        if(object==null){editing=null;return;}
        if(editing==null||editingGeneration!=snapshot.worldGeneration()||!object.id().equals(editing.id())||(!(findFocus() instanceof EditText)&&object.revision()!=editing.revision())){
            editing=object;editingGeneration=snapshot.worldGeneration();validationMessage="";
            fields.get("name").setText(object.name());
            double[] values={object.position().x,object.position().y,object.position().z,object.color().x,object.color().y,object.color().z,object.intensity(),object.range()};
            int i=0;for(String key:List.of("x","y","z","red","green","blue","intensity","range"))fields.get(key).setText(Double.toString(values[i++]));
            enabled.setChecked(object.enabled());
        }
    }
    private void apply(){
        var snapshot=ClientSceneService.INSTANCE.snapshot();
        if(editing==null||editingGeneration!=snapshot.worldGeneration()||owner.mode()!=ViewportMode.EDIT||!snapshot.canEdit()||snapshot.pending())return;
        try{
            validationMessage="";
            Vec3 position=new Vec3(value("x"),value("y"),value("z"));
            Vec3 color=new Vec3(value("red"),value("green"),value("blue"));
            float intensity=(float)value("intensity"),range=(float)value("range");
            if(color.x<0||color.y<0||color.z<0||color.x>1||color.y>1||color.z>1||intensity<0||range<=0)throw new IllegalArgumentException();
            ClientSceneService.INSTANCE.update(editingGeneration,editing.withPosition(position).withProperties(fields.get("name").getText().toString(),color,intensity,range,enabled.isChecked()));
            apply.requestFocus();
        }catch(RuntimeException invalid){validationMessage=tr("invalid");status.setText(validationMessage);}
    }
    private double value(String key){double value=Double.parseDouble(fields.get(key).getText().toString());if(!Double.isFinite(value))throw new IllegalArgumentException();return value;}
    private static String tr(String key){return Component.translatable("geometry_node.game_view.scene."+key).getString();}
}
