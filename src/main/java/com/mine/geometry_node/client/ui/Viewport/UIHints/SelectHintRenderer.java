package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeProperty;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.client.ui.Viewport.Viewport;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.Spinner;
import icyllis.modernui.widget.TextView;

public class SelectHintRenderer implements UIHintRenderer {
    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        String propKey = row.hintParams() != null ? (String) row.hintParams().get("property_key") : null;
        String[] options = row.hintParams() != null ? (String[]) row.hintParams().get("options") : new String[0];

//        if (row.hintParams() != null) {
//            propKey = (String) row.hintParams().get(PortRow.PARAM_PROPERTY_KEY);
//            options = (String[]) row.hintParams().getOrDefault(PortRow.PARAM_OPTIONS, new String[0]);
//        }

        // 核心修复：优先读取已保存的值
        Object val = null;
        if (propKey != null) {
            val = nodeData.properties.get(propKey);
        } else if (row.leftPort() != null) {
            val = nodeData.inputs.containsKey(row.leftPort().id()) ? nodeData.inputs.get(row.leftPort().id()) : row.leftPort().defaultValue();
        }

        Spinner spinner = new Spinner(context);

        ShapeDrawable borderBg = new ShapeDrawable();
        borderBg.setColor(0x05FFFFFF); // 极淡的白色填充
        borderBg.setCornerRadius(3);
        borderBg.setStroke(1, 0xFF555555); // 灰色边框
        spinner.setBackground(borderBg);

        ShapeDrawable popupBg = new ShapeDrawable();
        popupBg.setColor(0xFFFFFFFF); // 纯白底色
        popupBg.setCornerRadius(2);
        popupBg.setStroke(1, 0xFFCCCCCC); // 浅灰边框
        spinner.setPopupBackgroundDrawable(popupBg);

        // 2. 配置适配器
        spinner.setAdapter(new icyllis.modernui.widget.BaseAdapter() {
            @Override public int getCount() { return options.length; }
            @Override public Object getItem(int position) { return options[position]; }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, icyllis.modernui.view.ViewGroup parent) {
                TextView tv = (TextView) convertView;
                if (tv == null) {
                    tv = new TextView(context);
                    tv.setTextColor(UIConstants.CLR_GRAY_LABEL);
                    tv.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);
                    tv.setGravity(icyllis.modernui.view.Gravity.CENTER_VERTICAL);
                    tv.setPadding(6, 0, 6, 0);
                }
                tv.setText(options[position]);
                return tv;
            }

            @Override
            public View getDropDownView(int position, View convertView, icyllis.modernui.view.ViewGroup parent) {
                TextView tv = (TextView) convertView;
                if (tv == null) {
                    tv = new TextView(context);
                    tv.setTextColor(0xFF000000);      // 纯黑字体
                    tv.setTextSize(12);
                    tv.setPadding(16, 12, 16, 12);
                }
                tv.setText(options[position]);
                return tv;
            }
        });

        // 3. 恢复默认选中项 (从 AbsSpinner 继承的 setSelection)
        if (val != null) {
            for (int i = 0; i < options.length; i++) {
                if (options[i].equals(val.toString())) {
                    spinner.setSelection(i);
                    break;
                }
            }
        }

        // 4. 动态修正下拉列表缩放宽度
        spinner.setOnTouchListener((v, event) -> {
            if (event.getAction() == icyllis.modernui.view.MotionEvent.ACTION_DOWN) {
                icyllis.modernui.view.ViewParent parent = v.getParent();
                while (parent != null && !(parent instanceof Viewport)) {
                    parent = parent.getParent();
                }
                if (parent instanceof Viewport) {
                    float currentScale = ((Viewport) parent).getCurrentScale();
                    int scaledWidth = (int) (v.getWidth() * currentScale);
                    spinner.setDropDownWidth(scaledWidth);
                }
            }
            return false;
        });

        // 5. 监听选中事件
        spinner.setOnItemSelectedListener(new icyllis.modernui.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(icyllis.modernui.widget.AdapterView<?> parent, View view, int position, long id) {
                String selectedVal = options[position];
                if (editorContext != null) {
                    if (propKey != null) {
                        Object oldVal = nodeData.properties.get(propKey);
                        if (oldVal == null || !selectedVal.equals(oldVal.toString())) {
                            CmdChangeProperty cmd = new CmdChangeProperty(editorContext.getGraphController(), nodeData.id, propKey, oldVal, selectedVal);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    } else if (row.leftPort() != null) {
                        String portId = row.leftPort().id();
                        Object oldVal = nodeData.inputs.get(portId);
                        if (oldVal == null || !selectedVal.equals(oldVal.toString())) {
                            CmdChangeInputValue cmd = new CmdChangeInputValue(editorContext.getGraphController(), nodeData.id, portId, oldVal, selectedVal);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    }
                } else { // 兜底
                    if (propKey != null) nodeData.properties.put(propKey, selectedVal);
                    else if (row.leftPort() != null) nodeData.inputs.put(row.leftPort().id(), selectedVal);
                }
            }
            @Override
            public void onNothingSelected(icyllis.modernui.widget.AdapterView<?> parent) {}
        });

        return spinner;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        int leftMargin = (row.leftPort() != null) ? (int)(nodeWidth * 0.45f) : UIConstants.Node.LABEL_MARGIN_PORT;
        int rightMargin = (row.rightPort() != null) ? UIConstants.Node.ROW_HEIGHT : UIConstants.Node.LABEL_MARGIN_PORT;

        int targetWidth = nodeWidth - leftMargin - rightMargin;
        if (targetWidth < 10) targetWidth = 10;
        int targetHeight = UIConstants.Node.ROW_HEIGHT - 4; // 留出上下间隙

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(targetWidth, targetHeight);
        } else {
            lp.width = targetWidth;
            lp.height = targetHeight;
        }

        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = leftMargin;
        lp.topMargin = (int) currentY + 2; // 垂直居中偏移

        view.setLayoutParams(lp);
    }
}
