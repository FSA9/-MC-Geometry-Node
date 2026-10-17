package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.client.ui.components.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.common.UiCheckBox;
import com.mine.geometry_node.client.ui.components.common.UiSearchInput;
import com.mine.geometry_node.client.ui.components.common.SvgIconView;
import com.mine.geometry_node.client.ui.components.common.UiIconButton;
import com.mine.geometry_node.client.ui.components.common.UiActionButton;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.view.View;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.widget.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.function.*;

/** Menu views share the generic popover and keep scene writes outside popup rendering. */
public final class ViewportSceneMenus {
    private final View owner;
    private final FrameLayout host;
    private final ViewportVisualization state;
    private final Runnable changed;
    private UiPopover open;
    private long epoch;
    public ViewportSceneMenus(View owner,FrameLayout host,ViewportVisualization state,Runnable changed){this.owner=owner;this.host=host;this.state=state;this.changed=changed;}
    public boolean dismissIfOutside(float rootX, float rootY) {
        if (open == null || !open.dismissIfOutside(rootX, rootY)) return false;
        dismiss();
        return true;
    }
    public boolean isOpen(){return open!=null&&open.isOpen();}
    public void dismiss(){epoch++;if(open!=null)open.dismiss();open=null;}
    public void createAt(float rootX,float rootY,Vec3 position,Runnable created){
        dismiss();
        long token=epoch;
        var snapshot=ClientSceneService.INSTANCE.snapshot();
        LinearLayout column=column();
        column.addView(label(tr("add")),new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(25)));
        UiSearchInput search=new UiSearchInput(owner.getContext());
        search.setHint(Component.translatable("geometry_node.asset_library.toolbar.search").getString());
        column.addView(search,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(28)));
        LinearLayout choices=column();column.addView(choices,new LinearLayout.LayoutParams(-1,-2));
        String[] category={""};
        java.util.concurrent.atomic.AtomicReference<Consumer<String>> filterRef=new java.util.concurrent.atomic.AtomicReference<>();
        Consumer<String> filter=query->{
            if(token!=epoch)return;
            choices.removeAllViews();String needle=query.toLowerCase(java.util.Locale.ROOT).strip();
            if(!category[0].isEmpty()){
                View back=createItem("‹ "+tr("back"),true,()->{category[0]="";search.setText("");filterRef.get().accept("");});
                choices.addView(back,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(30)));
            }
            java.util.Set<String> categories=new java.util.LinkedHashSet<>();
            for(var entry:SceneCreateRegistry.entries()){
                if(needle.isEmpty()&&category[0].isEmpty()&&!entry.category().isEmpty()){
                    if(categories.add(entry.category())){
                        String group=entry.category();
                        choices.addView(createItem(tr(group)+" ›",true,()->{category[0]=group;filterRef.get().accept("");}),new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(30)));
                    }
                    continue;
                }
                if(!category[0].isEmpty()&&!entry.category().equals(category[0]))continue;
                String name=Component.translatable(entry.labelKey()).getString();
                if(!name.toLowerCase(java.util.Locale.ROOT).contains(needle)&&!entry.typeId().contains(needle))continue;
                View row=createItem(name,snapshot.canEdit()&&!snapshot.pending(),()->{
                    if(token!=epoch||!isOpen())return;dismiss();entry.create().accept(snapshot.worldGeneration(),position);created.run();
                });
                choices.addView(row,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(30)));
            }
            if(open!=null&&open.isOpen())open.refreshSize();
        };
        filterRef.set(filter);
        filter.accept("");search.setOnQueryChanged(filter);
        if(!snapshot.canEdit())column.addView(label(tr("read_only")),new LinearLayout.LayoutParams(-1,-2));
        open=new UiPopover(owner.getContext(),column,220);open.showAtRoot(owner,host,rootX,rootY);search.post(()->{if(search.isAttachedToWindow())search.requestFocus();});
    }
    private View createItem(String name,boolean enabled,Runnable action){
        UiActionButton row=UiActionButton.withPalette(owner.getContext(),name,
                new UiActionButton.Palette(0,UiTheme.Control.SELECTED,UiTheme.Control.ICON_PRESSED,UiTheme.Text.NORMAL),
                UiActionButton.Density.COMPACT,new UiActionButton.Style(4,0,0,UiTheme.Metrics.DISABLED_ALPHA));
        row.setGravity(Gravity.LEFT|Gravity.CENTER_VERTICAL);row.setPadding(UIUtils.dp2pxInt(5),0,UIUtils.dp2pxInt(5),0);
        row.setMinWidth(0);row.setMinHeight(0);row.setEnabled(enabled);row.setOnClickListener(v->action.run());return row;
    }
    public void modes(View anchor, com.mine.geometry_node.client.ui.editor.game.ViewportMode current,
                      Consumer<com.mine.geometry_node.client.ui.editor.game.ViewportMode> select) {
        dismiss();
        LinearLayout column = column();
        for (var mode : com.mine.geometry_node.client.ui.editor.game.ViewportMode.values()) {
            String name = Component.translatable("geometry_node.game_view.mode."
                    + mode.name().toLowerCase(java.util.Locale.ROOT)).getString();
            column.addView(createItem((mode == current ? "✓ " : "   ") + name, true, () -> {
                dismiss();
                select.accept(mode);
            }), new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(30)));
        }
        open = new UiPopover(owner.getContext(), column, 150);
        open.showBelow(anchor, host);
    }

    public void visualization(View anchor){
        dismiss();LinearLayout column=column();
        column.addView(label(tr("visibility")),new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(26)));
        LinearLayout headings=new LinearLayout(owner.getContext());headings.setOrientation(LinearLayout.HORIZONTAL);
        headings.addView(label(tr("type")),new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(24),1));
        headings.addView(centeredLabel(tr("selectable")),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(24)));
        headings.addView(centeredLabel(tr("visible")),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(24)));
        column.addView(headings);
        for(var layer:ViewportVisualization.Layer.values()){
            LinearLayout row=new LinearLayout(owner.getContext());row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label(tr("layer."+layer.name().toLowerCase(java.util.Locale.ROOT))),new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
            View selectToggle=iconToggle(SvgIconView.Icon.SCENE_CURSOR,state.snapshot().selectable().contains(layer),com.mine.geometry_node.client.runtime.game.scene.ScenePresentations.selectable(layer)&&state.visible(layer),b->{state.setSelectable(layer,b);changed.run();});
            row.addView(selectToggle,new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(20)));
            row.addView(iconToggle(SvgIconView.Icon.SCENE_EYE,state.visible(layer),true,b->{state.setVisible(layer,b);selectToggle.setEnabled(b&&com.mine.geometry_node.client.runtime.game.scene.ScenePresentations.selectable(layer));changed.run();}),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(20)));
            column.addView(row);
        }
        option(column,"helpers",state.helpers(),b->{state.setHelpers(b);changed.run();});
        option(column,"gizmo",state.gizmo(),b->{state.setGizmo(b);changed.run();});
        open=new UiPopover(owner.getContext(),column,240);open.showBelow(anchor,host);
    }
    private void option(LinearLayout column,String key,boolean value,Consumer<Boolean> change){
        LinearLayout row=new LinearLayout(owner.getContext());row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(tr(key)),new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
        row.addView(toggle(value,true,change),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(20)));column.addView(row);
    }
    private View iconToggle(SvgIconView.Icon kind,boolean value,boolean enabled,Consumer<Boolean> change){
        boolean[] checked={value};long token=epoch;
        SvgIconView icon=new SvgIconView(owner.getContext(),kind,value?UiTheme.Text.STRONG:UiTheme.Text.DISABLED);
        UiIconButton button=new UiIconButton(owner.getContext(),icon,new UiIconButton.Style(2,0,0,UiTheme.Control.ICON_HOVER,0));
        button.setEnabled(enabled);button.setSelected(value);
        button.tooltip(enabled?tr(kind==SvgIconView.Icon.SCENE_EYE?"visible":"selectable"):tr("selection_unavailable"));
        button.setOnClickListener(v->{if(token!=epoch||!isOpen())return;checked[0]=!checked[0];button.setSelected(checked[0]);icon.setIconColor(checked[0]?UiTheme.Text.STRONG:UiTheme.Text.DISABLED);change.accept(checked[0]);});
        return button;
    }
    private View toggle(boolean value,boolean enabled,Consumer<Boolean> change){
        FrameLayout box=new FrameLayout(owner.getContext());UiCheckBox check=new UiCheckBox(owner.getContext());check.setChecked(value);check.setEnabled(enabled);
        long token=epoch;
        check.setTooltipText(enabled?tr("toggle"):tr("selection_unavailable"));check.setOnCheckedChangeListener((v,b)->{if(token==epoch&&isOpen())change.accept(b);});
        box.addView(check,new FrameLayout.LayoutParams(UIUtils.dp2pxInt(18),UIUtils.dp2pxInt(18),Gravity.CENTER));return box;
    }
    private TextView centeredLabel(String text){TextView label=label(text);label.setGravity(Gravity.CENTER);return label;}
    private LinearLayout column(){LinearLayout c=new LinearLayout(owner.getContext());c.setOrientation(LinearLayout.VERTICAL);return c;}
    private TextView label(String text){TextView t=UIUtils.createLockedTextView(owner.getContext(),text,11,UiTheme.Text.NORMAL);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(UIUtils.dp2pxInt(5),0,UIUtils.dp2pxInt(5),0);return t;}
    private static String tr(String key){return Component.translatable("geometry_node.game_view.scene."+key).getString();}
}
