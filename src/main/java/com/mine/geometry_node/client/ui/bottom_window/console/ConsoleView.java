package com.mine.geometry_node.client.ui.bottom_window.console;

import com.mine.geometry_node.client.ui.session.DocumentManager;
import com.mine.geometry_node.client.ui.session.GraphSession;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import com.mine.geometry_node.core.node.NodeRegistry;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import icyllis.modernui.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ConsoleView extends FrameLayout implements ConsoleCommandRegistry.LogCallback {

    private final LinearLayout mLogContainer;
    private final EditText mInputBox;
    private final ScrollView mScrollView;

    // 补全列表容器与状态
    private final LinearLayout mSuggestionContainer;
    private static final List<String> KNOWN_COMMANDS = Arrays.asList("addnode", "delete", "connect", "clear");
    private final List<String> mCurrentSuggestions = new ArrayList<>();
    private int mSuggestionIndex = -1;

    // 历史记录状态
    private final List<String> mCommandHistory = new ArrayList<>();
    private int mHistoryIndex = -1;

    public ConsoleView(Context context) {
        super(context);
        setBackground(createColorDrawable(0xFF1E1E1E));

        // 1. 底层主视图
        LinearLayout mainLayout = new LinearLayout(context);
        mainLayout.setOrientation(LinearLayout.VERTICAL);

        mScrollView = new ScrollView(context);
        mLogContainer = new LinearLayout(context);
        mLogContainer.setOrientation(LinearLayout.VERTICAL);
        mLogContainer.setPadding(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(8));
        mScrollView.addView(mLogContainer, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        mainLayout.addView(mScrollView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        LinearLayout inputLayout = new LinearLayout(context);
        inputLayout.setOrientation(LinearLayout.HORIZONTAL);
        inputLayout.setGravity(Gravity.CENTER_VERTICAL);
        inputLayout.setBackground(createColorDrawable(0xFF252526));
        inputLayout.setPadding(UIUtils.dp2pxInt(8), 0, UIUtils.dp2pxInt(8), 0);

        TextView prompt = UIUtils.createLockedTextView(context, "geom-node $ ", 14f, 0xFF00AAFF);
        inputLayout.addView(prompt);

        mInputBox = new EditText(context);
        mInputBox.setTextColor(0xFFFFFFFF);
        mInputBox.setTextSize(TypedValue.COMPLEX_UNIT_PX, UIUtils.dp2px(14f));
        mInputBox.setBackground(null);
        mInputBox.setSingleLine(true);
        inputLayout.addView(mInputBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(32f)));

        mainLayout.addView(inputLayout, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(mainLayout, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 2. 顶层悬浮视图
        mSuggestionContainer = new LinearLayout(context);
        mSuggestionContainer.setOrientation(LinearLayout.VERTICAL);
        mSuggestionContainer.setBackground(createColorDrawable(0xFF333333));
        mSuggestionContainer.setVisibility(View.GONE);

        LayoutParams suggLp = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        suggLp.gravity = Gravity.BOTTOM | Gravity.LEFT;
        suggLp.bottomMargin = UIUtils.dp2pxInt(32f);
        suggLp.leftMargin = UIUtils.dp2pxInt(80f);
        addView(mSuggestionContainer, suggLp);

        // 3. 事件监听
        mInputBox.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                switch (keyCode) {
                    case KeyEvent.KEY_UP: return handleUpKey();
                    case KeyEvent.KEY_DOWN: return handleDownKey();
                    case KeyEvent.KEY_TAB: return handleTabKey();
                    case KeyEvent.KEY_ENTER: return handleEnterKey();
                }
            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                if (keyCode != KeyEvent.KEY_UP && keyCode != KeyEvent.KEY_DOWN &&
                        keyCode != KeyEvent.KEY_LEFT && keyCode != KeyEvent.KEY_RIGHT &&
                        keyCode != KeyEvent.KEY_ENTER && keyCode != KeyEvent.KEY_TAB) {
                    updateSuggestions(mInputBox.getText().toString());
                }
            }
            return false;
        });

        onLog("欢迎使用节点控制台。按 TAB 补全，按 ↑/↓ 浏览历史。", 0xFF888888);
    }

    // --- 键盘控制逻辑 ---

    private boolean handleUpKey() {
        if (mSuggestionContainer.getVisibility() == View.VISIBLE && !mCurrentSuggestions.isEmpty()) {
            mSuggestionIndex = Math.max(0, mSuggestionIndex - 1);
            renderSuggestions();
            return true;
        } else {
            if (mHistoryIndex > 0 && !mCommandHistory.isEmpty()) {
                mHistoryIndex--;
                mInputBox.setText(mCommandHistory.get(mHistoryIndex));
                mInputBox.setSelection(mInputBox.getText().length());
                mSuggestionContainer.setVisibility(View.GONE);
            }
            return true;
        }
    }

    private boolean handleDownKey() {
        if (mSuggestionContainer.getVisibility() == View.VISIBLE && !mCurrentSuggestions.isEmpty()) {
            mSuggestionIndex = Math.min(mCurrentSuggestions.size() - 1, mSuggestionIndex + 1);
            renderSuggestions();
            return true;
        } else {
            if (mHistoryIndex < mCommandHistory.size() - 1) {
                mHistoryIndex++;
                mInputBox.setText(mCommandHistory.get(mHistoryIndex));
            } else if (mHistoryIndex == mCommandHistory.size() - 1) {
                mHistoryIndex = mCommandHistory.size();
                mInputBox.setText("");
            }
            mInputBox.setSelection(mInputBox.getText().length());
            mSuggestionContainer.setVisibility(View.GONE);
            return true;
        }
    }

    private boolean handleTabKey() {
        if (mSuggestionContainer.getVisibility() == View.VISIBLE && mSuggestionIndex >= 0 && mSuggestionIndex < mCurrentSuggestions.size()) {
            String selected = mCurrentSuggestions.get(mSuggestionIndex);
            String currentInput = mInputBox.getText().toString();

            String newText;
            if (currentInput.toLowerCase().startsWith("addnode ") && currentInput.indexOf(' ') == currentInput.lastIndexOf(' ')) {
                newText = "addnode " + selected + " ";
            } else {
                newText = selected + " ";
            }

            mInputBox.setText(newText);
            mInputBox.setSelection(newText.length());

            updateSuggestions(newText);
        }
        return true;
    }

    private boolean handleEnterKey() {
        String text = mInputBox.getText().toString();
        mInputBox.setText("");
        mSuggestionContainer.setVisibility(View.GONE);
        onInputCommitted(text);
        return true;
    }

    private void updateSuggestions(String input) {
        mCurrentSuggestions.clear();
        mSuggestionIndex = -1;

        if (input.trim().isEmpty()) {
            mSuggestionContainer.setVisibility(View.GONE);
            return;
        }

        String lowerInput = input.toLowerCase();

        // 场景 A: 用户输入了 "addnode "，正在输入节点类型的参数
        if (lowerInput.startsWith("addnode ") && input.indexOf(' ') == input.lastIndexOf(' ')) {
            String typePrefix = input.substring(input.indexOf(' ') + 1).toLowerCase();

            // 从注册表中获取所有注册的 typeId，并根据前缀进行模糊匹配
            for (String typeId : NodeRegistry.INSTANCE.getAllTypeIds()) {
                if (typeId.toLowerCase().contains(typePrefix)) {
                    mCurrentSuggestions.add(typeId);
                }
            }
        }
        // 场景 B: 用户正在输入主指令，且没有敲击空格
        else if (!input.contains(" ")) {
            for (String cmd : KNOWN_COMMANDS) {
                if (cmd.startsWith(lowerInput)) {
                    mCurrentSuggestions.add(cmd);
                }
            }
        }

        if (mCurrentSuggestions.isEmpty()) {
            mSuggestionContainer.setVisibility(View.GONE);
        } else {
            mSuggestionIndex = 0; // 默认高亮第一项
            renderSuggestions();
            mSuggestionContainer.setVisibility(View.VISIBLE);
        }
    }

    private void renderSuggestions() {
        mSuggestionContainer.removeAllViews();
        Context context = getContext();

        // 为了防止补全列表过长溢出屏幕，最多显示 8 个候选项
        int displayLimit = Math.min(mCurrentSuggestions.size(), 8);

        for (int i = 0; i < displayLimit; i++) {
            boolean isSelected = (i == mSuggestionIndex);
            TextView tv = UIUtils.createLockedTextView(context, mCurrentSuggestions.get(i), 13f, isSelected ? 0xFFFFFFFF : 0xFFCCCCCC);
            tv.setPadding(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(24), UIUtils.dp2pxInt(6));

            if (isSelected) {
                tv.setBackground(createColorDrawable(0xFF0055AA));
            }
            mSuggestionContainer.addView(tv);
        }

        // 提示还有更多项
        if (mCurrentSuggestions.size() > displayLimit) {
            TextView moreTv = UIUtils.createLockedTextView(context, "... 更多匹配项", 11f, 0xFF888888);
            moreTv.setPadding(UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(4), UIUtils.dp2pxInt(12), UIUtils.dp2pxInt(4));
            mSuggestionContainer.addView(moreTv);
        }
    }

    // --- 执行与日志输出 ---

    private void onInputCommitted(String line) {
        if (line.trim().isEmpty()) return;

        if (mCommandHistory.isEmpty() || !mCommandHistory.get(mCommandHistory.size() - 1).equals(line)) {
            mCommandHistory.add(line);
        }
        mHistoryIndex = mCommandHistory.size();

        onLog("geom-node $ " + line, 0xFFFFFFFF);

        GraphSession activeSession = DocumentManager.INSTANCE.getActiveSession();
        ConsoleCommandRegistry.executeLine(line, activeSession, this);
    }

    @Override
    public void onLog(String text, int color) {
        TextView tv = new TextView(getContext());
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, UIUtils.dp2px(13f));
        tv.setTextColor(color);
        tv.setPadding(0, UIUtils.dp2pxInt(2), 0, UIUtils.dp2pxInt(2));
        tv.setTextIsSelectable(true);

        mLogContainer.addView(tv);
        mScrollView.post(() -> mScrollView.fullScroll(View_FOCUS_DOWN));
    }

    @Override
    public void onClear() {
        mLogContainer.removeAllViews();
    }

    public void requestInputFocus() {
        mInputBox.requestFocus();
    }

    private ShapeDrawable createColorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private static final int View_FOCUS_DOWN = 130;
}