package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSettings;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.LinearLayout;
import net.minecraft.network.chat.Component;

import java.util.EnumMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Arbitrary controls in UiPopover: target toggles stay open and never change the enable switch. */
final class ViewportSnapPanel extends LinearLayout {
    private final EnumMap<ViewportSnapSettings.Base,UiActionButton> bases=new EnumMap<>(ViewportSnapSettings.Base.class);
    private final EnumMap<ViewportSnapSettings.Target,UiActionButton> targets=new EnumMap<>(ViewportSnapSettings.Target.class);
    private final Supplier<ViewportSnapSettings> get;
    private final Consumer<ViewportSnapSettings> set;
    ViewportSnapPanel(Context context,Supplier<ViewportSnapSettings> get,Consumer<ViewportSnapSettings> set) {
        super(context);this.get=get;this.set=set;setOrientation(VERTICAL);
        heading("base");
        LinearLayout baseRow=new LinearLayout(context);baseRow.setOrientation(HORIZONTAL);
        for(var base:ViewportSnapSettings.Base.values()) {
            var button=button("base."+base.name().toLowerCase(java.util.Locale.ROOT),true,()->change(get.get().withBase(base)));
            bases.put(base,button);baseRow.addView(button,new LayoutParams(0,UIUtils.dp2pxInt(25),1));
        }
        addView(baseRow,new LayoutParams(-1,-2));
        heading("targets");
        for(var target:ViewportSnapSettings.Target.values()) {
            if(target==ViewportSnapSettings.Target.FACE_PROJECT)heading("individual_targets");
            var button=button("target."+target.key(),false,()->change(get.get().toggle(target)));
            targets.put(target,button);
            LayoutParams row=new LayoutParams(-1,UIUtils.dp2pxInt(25));row.topMargin=UIUtils.dp2pxInt(1);addView(button,row);
        }
        heading("selection");
        LinearLayout row=new LinearLayout(context);row.setOrientation(HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        UiCheckBox exclude=new UiCheckBox(context);exclude.setChecked(get.get().excludeUnselectable());
        row.addView(exclude,new LayoutParams(UIUtils.dp2pxInt(20),UIUtils.dp2pxInt(20)));
        var label=UIUtils.createLockedTextView(context,tr("exclude_unselectable"),11,UiTheme.Text.NORMAL);
        label.setGravity(Gravity.CENTER_VERTICAL);row.addView(label,new LayoutParams(0,UIUtils.dp2pxInt(27),1));
        label.setOnClickListener(v->exclude.performClick());
        exclude.setOnCheckedChangeListener((v,value)->change(get.get().withExcludeUnselectable(value)));
        addView(row,new LayoutParams(-1,-2));
        refresh();
    }
    private void change(ViewportSnapSettings value){set.accept(value);refresh();}
    private void refresh(){
        var current=get.get();
        bases.forEach((base,button)->select(button,current.base()==base));
        targets.forEach((target,button)->select(button,current.targets().contains(target)));
    }
    private static void select(UiActionButton button,boolean value){
        button.setSelected(value);
        button.setPalette(UiActionButton.Palette.toggle(value));
    }
    private UiActionButton button(String key,boolean center,Runnable click){
        var button=UiActionButton.withPalette(getContext(),tr(key),new UiActionButton.Palette(0,UiTheme.Control.ICON_HOVER,UiTheme.Control.ICON_PRESSED,UiTheme.Text.NORMAL),
                UiActionButton.Density.COMPACT,new UiActionButton.Style(0,0,0,UiTheme.Metrics.DISABLED_ALPHA));
        UIUtils.setLockedTextSize(button,11);button.setMinWidth(0);button.setMinHeight(0);
        button.setGravity((center?Gravity.CENTER:Gravity.LEFT|Gravity.CENTER_VERTICAL));
        button.setPadding(UIUtils.dp2pxInt(7),0,UIUtils.dp2pxInt(7),0);button.setOnClickListener(v->click.run());return button;
    }
    private void heading(String key){
        var label=UIUtils.createLockedTextView(getContext(),tr(key),11,UiTheme.Text.SECONDARY);
        label.setGravity(Gravity.CENTER_VERTICAL);addView(label,new LayoutParams(-1,UIUtils.dp2pxInt(25)));
    }
    private static String tr(String key){return Component.translatable("geometry_node.game_view.snap."+key).getString();}
}
