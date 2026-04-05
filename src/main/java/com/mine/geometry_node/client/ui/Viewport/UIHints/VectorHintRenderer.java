package com.mine.geometry_node.client.ui.Viewport.UIHints;

import com.mine.geometry_node.client.ui.UICommand.EditorContext;
import com.mine.geometry_node.client.ui.UICommand.commands.CmdChangeInputValue;
import com.mine.geometry_node.client.ui.UIConstants;
import com.mine.geometry_node.core.node.NodeData;
import com.mine.geometry_node.core.node.port.PortRow;
import icyllis.modernui.core.Context;
import icyllis.modernui.view.View;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

public class VectorHintRenderer implements UIHintRenderer {

    @Override
    public View createView(Context context, NodeData nodeData, PortRow row, EditorContext editorContext) {
        // 创建垂直布局容器
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);

        // 获取初始 List 值 (安全转换为 List)
        String portId = row.leftPort().id();
        Object rawVal = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();

        List<Number> vec = (rawVal instanceof List) ? (List<Number>) rawVal : List.of(0f, 0f, 0f);

        // 标签：X, Y, Z
        String[] labels = {"X: ", "Y: ", "Z: "};

        for (int i = 0; i < 3; i++) {
            final int index = i;
            EditText et = new EditText(context);
            // 显示时附带 XYZ 前缀帮助识别，如果 ModernUI 支持 Hint 就更好了
            et.setText(String.valueOf(vec.get(i).floatValue()));
            et.setTextColor(UIConstants.CLR_GRAY_LABEL);
            et.setTextSize(UIConstants.Node.TEXT_SIZE_LABEL);

            // 设置每个输入框占用的高度（单行高）
            LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    UIConstants.Node.ROW_HEIGHT - 4
            );
            etParams.bottomMargin = 4; // 稍微留点空隙
            container.addView(et, etParams);

            // 监听输入
            et.setOnFocusChangeListener((v, hasFocus) -> {
                if (!hasFocus && editorContext != null) {
                    try {
                        float parsedFloat = Float.parseFloat(et.getText().toString());

                        // 获取最新的旧值 (防止并发覆盖)
                        Object currentRaw = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
                        List<Number> currentVec = (currentRaw instanceof List) ? (List<Number>) currentRaw : List.of(0f, 0f, 0f);

                        // 比较是否变化
                        if (currentVec.get(index).floatValue() != parsedFloat) {
                            // 必须创建一个新 List 来保证命令的不可变性
                            List<Float> newList = new ArrayList<>();
                            for (int j = 0; j < 3; j++) {
                                newList.add(j == index ? parsedFloat : currentVec.get(j).floatValue());
                            }
                            CmdChangeInputValue cmd = new CmdChangeInputValue(
                                    editorContext.getGraphController(), nodeData.id, portId, currentRaw, newList);
                            editorContext.getCommandManager().execute(cmd);
                        }
                    } catch (NumberFormatException e) {
                        // 解析失败，恢复原值
                        Object fallbackRaw = nodeData.inputs.containsKey(portId) ? nodeData.inputs.get(portId) : row.leftPort().defaultValue();
                        List<Number> fallbackVec = (fallbackRaw instanceof List) ? (List<Number>) fallbackRaw : List.of(0f, 0f, 0f);
                        et.setText(String.valueOf(fallbackVec.get(index).floatValue()));
                    }
                }
            });
        }
        return container;
    }

    @Override
    public void updateLayout(View view, PortRow row, float currentY, int nodeWidth) {
        // 由于本体占了一行 (圆点+标题)，这三个输入框要排在它“下面”，也就是 Y 轴要加上 1 个 ROW_HEIGHT
        float startX = UIConstants.Node.LABEL_MARGIN_PORT; // 顶头对齐，给宽点空间
        float endX = nodeWidth - UIConstants.Node.LABEL_MARGIN_PORT;

        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = (int) (endX - startX);
        lp.height = (UIConstants.Node.ROW_HEIGHT) * 3; // 占据三行高
        lp.gravity = icyllis.modernui.view.Gravity.LEFT | icyllis.modernui.view.Gravity.TOP;
        lp.leftMargin = (int) startX;
        // 关键：往下偏移一行！
        lp.topMargin = (int) currentY + UIConstants.Node.ROW_HEIGHT;
        view.setLayoutParams(lp);
    }
}