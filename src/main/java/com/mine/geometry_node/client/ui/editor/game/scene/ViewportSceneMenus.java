package com.mine.geometry_node.client.ui.editor.game.scene;

import com.mine.geometry_node.client.ui.theme.UiThemes;
import com.mine.geometry_node.client.runtime.game.snap.ViewportSnapSettings;
import com.mine.geometry_node.client.feature.scene.ClientSceneService;
import com.mine.geometry_node.client.runtime.game.scene.ViewportVisualization;
import com.mine.geometry_node.client.runtime.game.scene.ScenePresentations;
import com.mine.geometry_node.client.ui.components.composite.menu.UiPopover;
import com.mine.geometry_node.client.ui.components.composite.menu.UiContextMenu;
import com.mine.geometry_node.client.ui.components.composite.menu.UiSearchMenuLayout;
import com.mine.geometry_node.client.ui.components.base.input.UiCheckBox;
import com.mine.geometry_node.client.ui.components.base.input.UiSearchInput;
import com.mine.geometry_node.client.ui.components.base.icon.SvgIconView;
import com.mine.geometry_node.client.ui.components.base.button.UiIconButton;
import com.mine.geometry_node.client.ui.components.base.menu.UiMenuItems;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.view.View;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.SpannableString;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.style.StyleSpan;
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
    private UiContextMenu context;
    private long epoch;
    public ViewportSceneMenus(View owner,FrameLayout host,ViewportVisualization state,Runnable changed){this.owner=owner;this.host=host;this.state=state;this.changed=changed;}
    public boolean dismissIfOutside(float rootX, float rootY) {
        if (context != null && context.dismissIfOutside(rootX, rootY)) { dismiss(); return true; }
        if (open == null || !open.dismissIfOutside(rootX, rootY)) return false;
        dismiss();
        return true;
    }
    public boolean isOpen(){return open!=null&&open.isOpen()||context!=null&&context.isOpen();}
    public void dismiss(){
        epoch++;
        if(open!=null)open.dismiss();open=null;
        UiContextMenu previous=context;context=null;if(previous!=null)previous.dismiss();
    }
    /** Context commands reuse the asset browser's menu, input barrier and shortcut column. */
    public void contextAt(float rootX, float rootY, Consumer<UiContextMenu> build) {
        dismiss();
        float[] point = {rootX, rootY};
        if (!UIUtils.rootToLocal(host, rootX, rootY, new icyllis.modernui.graphics.Matrix(), point)) return;
        UiContextMenu menu = new UiContextMenu(owner.getContext(), UiThemes.EDITOR);
        build.accept(menu);
        if (!menu.hasItems()) return;
        context = menu;
        menu.setOnDismissListener(() -> { if (context == menu) { context = null; epoch++; } });
        menu.showAt(point[0], point[1], host);
        if (!menu.isOpen() && context == menu) context = null;
    }
    public void createAt(float rootX,float rootY,Vec3 position,Runnable created){
        dismiss();
        long token=epoch;
        var snapshot=ClientSceneService.INSTANCE.snapshot();
        LinearLayout column=column();
        UiSearchInput search=UiSearchMenuLayout.addHeader(column,tr("add"),true);
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
                    if(token!=epoch||!isOpen())return;
                    var current=ClientSceneService.INSTANCE.snapshot();
                    if(current.worldGeneration()!=snapshot.worldGeneration()||!current.canEdit()||current.pending())return;
                    dismiss();
                    if(entry.layer()!=null){state.setVisible(entry.layer(),true);state.setSelectable(entry.layer(),true);}
                    if(entry.layer()==ViewportVisualization.Layer.INTERACTION)state.setHelpers(true);
                    entry.create().accept(snapshot.worldGeneration(),position);created.run();changed.run();
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
        return UiMenuItems.row(owner.getContext(),name,false,enabled,action);
    }
    public void modes(View anchor, com.mine.geometry_node.client.ui.editor.game.ViewportMode current,
                      Consumer<com.mine.geometry_node.client.ui.editor.game.ViewportMode> select) {
        dismiss();
        long token = epoch;
        LinearLayout column = column();
        for (var mode : com.mine.geometry_node.client.ui.editor.game.ViewportMode.values()) {
            String name = Component.translatable("geometry_node.game_view.mode."
                    + mode.name().toLowerCase(java.util.Locale.ROOT)).getString();
            column.addView(createItem((mode == current ? "✓ " : "   ") + name, true, () -> {
                if (token != epoch || !isOpen()) return;
                dismiss();
                select.accept(mode);
            }), new LinearLayout.LayoutParams(-1, UIUtils.dp2pxInt(30)));
        }
        open = new UiPopover(owner.getContext(), column, 150);
        open.showBelow(anchor, host);
    }

    public void visualization(View anchor){
        dismiss();LinearLayout column=column();
        TextView title = label(tr("visibility"));
        SpannableString titleText = new SpannableString(title.getText());
        titleText.setSpan(new StyleSpan(Typeface.BOLD), 0, titleText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        title.setText(titleText);
        column.addView(title,new LinearLayout.LayoutParams(-1,UIUtils.dp2pxInt(26)));
        LinearLayout headings=new LinearLayout(owner.getContext());headings.setOrientation(LinearLayout.HORIZONTAL);
        headings.addView(label(tr("type")),new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(24),1));
        headings.addView(centeredLabel(tr("selectable")),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(24)));
        headings.addView(centeredLabel(tr("visible")),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(24)));
        column.addView(headings);
        java.util.List<Runnable> refresh = new java.util.ArrayList<>();
        var layers = ViewportVisualization.Layer.values();
        LinearLayout all = new LinearLayout(owner.getContext());
        all.setOrientation(LinearLayout.HORIZONTAL);
        all.setGravity(Gravity.CENTER_VERTICAL);
        all.addView(label(tr("all_types")), new LinearLayout.LayoutParams(0, UIUtils.dp2pxInt(28), 1));
        all.addView(iconToggle(SvgIconView.Icon.SCENE_CURSOR,
                () -> java.util.Arrays.stream(layers).filter(ScenePresentations::selectable)
                        .allMatch(layer -> state.snapshot().selectable().contains(layer)),
                () -> java.util.Arrays.stream(layers).anyMatch(layer -> ScenePresentations.selectable(layer) && state.visible(layer)),
                value -> {
                    for (var layer : layers) if (ScenePresentations.selectable(layer)) state.setSelectable(layer, value);
                    changed.run();
                }, refresh), new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48), UIUtils.dp2pxInt(20)));
        all.addView(iconToggle(SvgIconView.Icon.SCENE_EYE,
                () -> java.util.Arrays.stream(layers).allMatch(state::visible), () -> true,
                value -> {
                    for (var layer : layers) state.setVisible(layer, value);
                    changed.run();
                }, refresh), new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48), UIUtils.dp2pxInt(20)));
        column.addView(all);
        for(var layer:ViewportVisualization.Layer.values()){
            LinearLayout row=new LinearLayout(owner.getContext());row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(label(tr("layer."+layer.name().toLowerCase(java.util.Locale.ROOT))),new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
            View selectToggle=iconToggle(SvgIconView.Icon.SCENE_CURSOR,()->state.snapshot().selectable().contains(layer),()->ScenePresentations.selectable(layer)&&state.visible(layer),b->{state.setSelectable(layer,b);changed.run();},refresh);
            row.addView(selectToggle,new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(20)));
            row.addView(iconToggle(SvgIconView.Icon.SCENE_EYE,()->state.visible(layer),()->true,b->{state.setVisible(layer,b);changed.run();},refresh),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(20)));
            column.addView(row);
        }
        option(column,"helpers",state.helpers(),b->{state.setHelpers(b);changed.run();});
        option(column,"gizmo",state.gizmo(),b->{state.setGizmo(b);changed.run();});
        open=new UiPopover(owner.getContext(),column,240);open.showBelow(anchor,host);
    }
    public void snapping(View anchor,Supplier<ViewportSnapSettings> get,
                         Consumer<ViewportSnapSettings> set){
        dismiss();long token=epoch;
        var panel=new ViewportSnapPanel(owner.getContext(),get,value->{if(token==epoch&&isOpen())set.accept(value);});
        open=new UiPopover(owner.getContext(),panel,220);open.showBelow(anchor,host);
    }
    private void option(LinearLayout column,String key,boolean value,Consumer<Boolean> change){
        LinearLayout row=new LinearLayout(owner.getContext());row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(label(tr(key)),new LinearLayout.LayoutParams(0,UIUtils.dp2pxInt(28),1));
        row.addView(toggle(value,true,change),new LinearLayout.LayoutParams(UIUtils.dp2pxInt(48),UIUtils.dp2pxInt(20)));column.addView(row);
    }
    private View iconToggle(SvgIconView.Icon kind,BooleanSupplier value,BooleanSupplier enabled,Consumer<Boolean> change,java.util.List<Runnable> refresh){
        long token=epoch;
        SvgIconView icon=new SvgIconView(owner.getContext(),kind,UiThemes.EDITOR.text().strong());
        UiIconButton button=UiIconButton.createThemed(owner.getContext(),UiThemes.EDITOR,icon,UiIconButton.Preset.INLINE);
        Runnable update = () -> {
            button.setEnabled(enabled.getAsBoolean());
            boolean selected = value.getAsBoolean();
            button.setSelected(selected);
            icon.setIconColor(selected ? UiThemes.EDITOR.text().strong() : UiThemes.EDITOR.text().disabled());
            button.tooltip(button.isEnabled()?tr(kind==SvgIconView.Icon.SCENE_EYE?"visible":"selectable"):tr("selection_unavailable"));
        };
        refresh.add(update);
        update.run();
        button.setOnClickListener(v->{if(token!=epoch||!isOpen())return;change.accept(!value.getAsBoolean());refresh.forEach(Runnable::run);});
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
    private TextView label(String text){TextView t=UIUtils.createLockedTextView(owner.getContext(),text,11,UiThemes.EDITOR.text().normal());t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(UIUtils.dp2pxInt(5),0,UIUtils.dp2pxInt(5),0);return t;}
    private static String tr(String key){return Component.translatable("geometry_node.game_view.scene."+key).getString();}
}
