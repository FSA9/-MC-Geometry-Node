package com.mine.geometry_node.client.ui.editor.terminal;

import com.mine.geometry_node.client.terminal.TerminalExit;
import com.mine.geometry_node.client.terminal.TerminalRunState;
import com.mine.geometry_node.client.terminal.TerminalSize;
import com.mine.geometry_node.client.terminal.emulator.TerminalCell;
import com.mine.geometry_node.client.terminal.emulator.TerminalSnapshot;
import com.mine.geometry_node.client.terminal.emulator.TerminalStyle;
import com.mine.geometry_node.client.terminal.input.TerminalKey;
import com.mine.geometry_node.client.terminal.shell.ShellTerminalCoordinator;
import com.mine.geometry_node.client.terminal.shell.ShellTerminalObserver;
import com.mine.geometry_node.client.ui.utils.UIUtils;
import icyllis.modernui.core.Context;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.resources.TypedValue;
import icyllis.modernui.text.Editable;
import icyllis.modernui.text.SpannableStringBuilder;
import icyllis.modernui.text.Spanned;
import icyllis.modernui.text.TextWatcher;
import icyllis.modernui.text.Typeface;
import icyllis.modernui.text.style.BackgroundColorSpan;
import icyllis.modernui.text.style.ForegroundColorSpan;
import icyllis.modernui.text.style.StyleSpan;
import icyllis.modernui.text.style.UnderlineSpan;
import icyllis.modernui.view.Gravity;
import icyllis.modernui.view.KeyEvent;
import icyllis.modernui.view.MotionEvent;
import icyllis.modernui.view.View;
import icyllis.modernui.view.ViewGroup;
import icyllis.modernui.widget.EditText;
import icyllis.modernui.widget.FrameLayout;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.ScrollView;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.Minecraft;

import java.util.concurrent.atomic.AtomicBoolean;

/** ModernUI renderer/input surface for one interactive PTY-backed SHELL tab. */
public final class ShellTerminalView extends LinearLayout implements ShellTerminalObserver {
    private static final float CELL_HEIGHT_DP = 17f;
    private static final int STATUS_HEIGHT_DP = 24;
    private static final long RENDER_INTERVAL_MILLIS = 50;

    private final ShellTerminalCoordinator coordinator;
    private final ScrollView scrollView;
    private final TextView screenView;
    private final EditText inputSink;
    private final TextView statusView;
    private final TextView actionView;
    private final float cellWidthPx;
    private final int cellHeightPx;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();

    private volatile TerminalRunState displayedState = TerminalRunState.IDLE;
    private volatile String pendingError = "";
    private volatile TerminalSize pendingSize = new TerminalSize(80, 24);
    private TerminalSize appliedSize = new TerminalSize(80, 24);
    private boolean changingInput;
    private volatile boolean startRequested;
    private volatile boolean disposed;

    public ShellTerminalView(Context context, ShellTerminalCoordinator coordinator) {
        super(context);
        this.coordinator = coordinator;
        coordinator.setObserver(this);
        setOrientation(VERTICAL);
        setBackground(colorDrawable(TerminalStyle.DEFAULT_BACKGROUND));

        LinearLayout statusBar = new LinearLayout(context);
        statusBar.setOrientation(HORIZONTAL);
        statusBar.setGravity(Gravity.CENTER_VERTICAL);
        statusBar.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(8), 0);
        statusBar.setBackground(colorDrawable(0xFF252526));
        statusView = UIUtils.createLockedTextView(context, "PowerShell", 12f, 0xFFAAAAAA);
        statusBar.addView(statusView, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        actionView = UIUtils.createLockedTextView(context, "Start", 12f, 0xFF4FC1FF);
        actionView.setGravity(Gravity.CENTER);
        actionView.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(10), 0);
        actionView.setOnClickListener(ignored -> toggleProcess());
        statusBar.addView(actionView, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        addView(statusBar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(STATUS_HEIGHT_DP)));

        FrameLayout terminalSurface = new FrameLayout(context);
        scrollView = new ScrollView(context);
        scrollView.setFillViewport(true);
        screenView = new TextView(context);
        screenView.setTypeface(Typeface.MONOSPACED);
        screenView.setTextSize(TypedValue.COMPLEX_UNIT_PX, UIUtils.dp2px(13f));
        screenView.setTextColor(TerminalStyle.DEFAULT_FOREGROUND);
        screenView.setBackground(colorDrawable(TerminalStyle.DEFAULT_BACKGROUND));
        screenView.setPadding(UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(6), UIUtils.dp2pxInt(8), UIUtils.dp2pxInt(6));
        screenView.setIncludeFontPadding(false);
        cellHeightPx = UIUtils.dp2pxInt(CELL_HEIGHT_DP);
        screenView.setLineHeight(cellHeightPx);
        screenView.setHorizontallyScrolling(true);
        cellWidthPx = Math.max(1f, screenView.getPaint().measureTextRun("M", 0, 1, false, null));
        screenView.setTextIsSelectable(true);
        scrollView.addView(screenView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        terminalSurface.addView(scrollView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        inputSink = new EditText(context);
        inputSink.setSingleLine(false);
        inputSink.setTextColor(0x00000000);
        inputSink.setBackground(null);
        inputSink.setPadding(0, 0, 0, 0);
        inputSink.setFocusable(true);
        inputSink.setFocusableInTouchMode(true);
        inputSink.setOnKeyListener((view, keyCode, event) -> handleKey(keyCode, event));
        inputSink.addTextChangedListener(new InputCommitWatcher());
        FrameLayout.LayoutParams inputParams = new FrameLayout.LayoutParams(1, 1);
        inputParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        terminalSurface.addView(inputSink, inputParams);
        terminalSurface.setOnTouchListener(this::refocusInputAfterTouch);
        scrollView.setOnTouchListener(this::refocusInputAfterTouch);
        screenView.setOnTouchListener(this::refocusInputAfterTouch);
        addView(terminalSurface, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        renderSnapshot(coordinator.snapshot());
        updateStatus();
    }

    public void ensureStarted() {
        if (disposed || startRequested || displayedState.hasActiveBackend()) return;
        startRequested = true;
        pendingError = "";
        coordinator.startPowerShell(pendingSize);
        requestRefresh();
    }

    public void requestInputFocus() {
        if (disposed) return;
        inputSink.requestFocus();
        inputSink.post(() -> {
            if (!disposed) inputSink.requestFocus();
        });
    }

    public void dispose() {
        disposed = true;
        coordinator.dispose();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        int usableWidth = Math.max(1, width - UIUtils.dp2pxInt(16));
        int usableHeight = Math.max(1, height - UIUtils.dp2pxInt(STATUS_HEIGHT_DP + 12));
        int columns = Math.max(2, (int) (usableWidth / cellWidthPx));
        int rows = Math.max(1, usableHeight / cellHeightPx);
        pendingSize = new TerminalSize(columns, rows);
        requestRefresh();
    }

    @Override public void onScreenChanged() { requestRefresh(); }

    @Override
    public void onStateChanged(TerminalRunState state) {
        displayedState = state;
        if (state == TerminalRunState.RUNNING) {
            startRequested = true;
            post(this::requestInputFocus);
        }
        if (state == TerminalRunState.EXITED || state == TerminalRunState.FAILED) startRequested = false;
        requestRefresh();
    }

    @Override
    public void onExited(TerminalExit exit) {
        if (exit.failed() || exit.exitCode() != null && exit.exitCode() != 0) pendingError = exit.message();
        requestRefresh();
    }

    @Override
    public void onError(String message) {
        pendingError = message;
        startRequested = false;
        requestRefresh();
    }

    private void requestRefresh() {
        if (disposed) return;
        dirty.set(true);
        if (refreshQueued.compareAndSet(false, true)) postDelayed(this::drainRefresh, RENDER_INTERVAL_MILLIS);
    }

    private void drainRefresh() {
        if (disposed) return;
        dirty.set(false);
        TerminalSize targetSize = pendingSize;
        if (!targetSize.equals(appliedSize)) {
            appliedSize = targetSize;
            coordinator.resize(targetSize);
        }
        renderSnapshot(coordinator.snapshot());
        updateStatus();
        refreshQueued.set(false);
        if (dirty.get()) requestRefresh();
    }

    private void renderSnapshot(TerminalSnapshot snapshot) {
        boolean retainInputFocus = inputSink.isFocused();
        boolean followBottom = scrollView.getScrollY() + scrollView.getHeight()
                >= Math.max(0, screenView.getHeight() - UIUtils.dp2pxInt(CELL_HEIGHT_DP * 2));
        SpannableStringBuilder text = new SpannableStringBuilder();
        int cursorStart = -1;
        for (int lineIndex = 0; lineIndex < snapshot.lines().size(); lineIndex++) {
            var line = snapshot.lines().get(lineIndex);
            TerminalStyle runStyle = null;
            int runStart = text.length();
            for (int column = 0; column < line.size(); column++) {
                TerminalCell cell = line.get(column);
                if (cell.width() == 0) continue;
                if (runStyle != null && !runStyle.equals(cell.style())) {
                    applyStyle(text, runStart, text.length(), runStyle);
                    runStart = text.length();
                }
                runStyle = cell.style();
                if (lineIndex == snapshot.cursorLine() && column == snapshot.cursorColumn()) cursorStart = text.length();
                text.append(cell.text().isEmpty() ? " " : cell.text());
            }
            if (runStyle != null) applyStyle(text, runStart, text.length(), runStyle);
            if (lineIndex + 1 < snapshot.lines().size()) text.append('\n');
        }
        if (snapshot.cursorVisible() && cursorStart >= 0 && cursorStart < text.length()) {
            text.setSpan(new BackgroundColorSpan(0xFFD4D4D4), cursorStart, cursorStart + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new ForegroundColorSpan(0xFF1E1E1E), cursorStart, cursorStart + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        screenView.setText(text);
        if (followBottom) {
            scrollView.post(() -> scrollView.scrollTo(0,
                    Math.max(0, screenView.getHeight() - scrollView.getHeight())));
        }
        if (retainInputFocus) inputSink.post(() -> {
            if (!disposed) inputSink.requestFocus();
        });
    }

    private static void applyStyle(SpannableStringBuilder text, int start, int end, TerminalStyle style) {
        if (start >= end) return;
        int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
        text.setSpan(new ForegroundColorSpan(style.effectiveForeground()), start, end, flags);
        if (style.effectiveBackground() != TerminalStyle.DEFAULT_BACKGROUND) {
            text.setSpan(new BackgroundColorSpan(style.effectiveBackground()), start, end, flags);
        }
        if (style.bold()) text.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
        if (style.underline()) text.setSpan(new UnderlineSpan(), start, end, flags);
    }

    private boolean handleKey(int keyCode, KeyEvent event) {
        if (event.getAction() != KeyEvent.ACTION_DOWN) return isSpecialKey(keyCode) || event.isCtrlPressed();
        if (event.isCtrlPressed()) {
            if (keyCode == KeyEvent.KEY_C) coordinator.interrupt();
            else if (keyCode == KeyEvent.KEY_V) coordinator.paste(Minecraft.getInstance().keyboardHandler.getClipboard());
            else {
                Character control = controlCharacter(keyCode);
                if (control != null) coordinator.sendControl(control);
            }
            return true;
        }
        TerminalKey key = mapKey(keyCode);
        if (key == null) return false;
        coordinator.sendKey(key);
        return true;
    }

    private boolean refocusInputAfterTouch(View view, MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_UP) requestInputFocus();
        return false;
    }

    private void toggleProcess() {
        if (displayedState.hasActiveBackend()) coordinator.stop();
        else ensureStarted();
    }

    private void updateStatus() {
        String detail = pendingError.isBlank() ? displayedState.name() : pendingError;
        statusView.setText("PowerShell  " + detail);
        actionView.setText(displayedState.hasActiveBackend() ? "Stop" : "Start");
        statusView.setTextColor(pendingError.isBlank() ? 0xFFAAAAAA : 0xFFF48771);
    }

    private static boolean isSpecialKey(int keyCode) { return mapKey(keyCode) != null; }

    private static Character controlCharacter(int keyCode) {
        if (keyCode >= KeyEvent.KEY_A && keyCode <= KeyEvent.KEY_Z) {
            return (char) ('A' + keyCode - KeyEvent.KEY_A);
        }
        if (keyCode == KeyEvent.KEY_SPACE) return '@';
        if (keyCode == KeyEvent.KEY_LEFT_BRACKET) return '[';
        if (keyCode == KeyEvent.KEY_BACKSLASH) return '\\';
        if (keyCode == KeyEvent.KEY_RIGHT_BRACKET) return ']';
        return null;
    }

    private static TerminalKey mapKey(int keyCode) {
        if (keyCode == KeyEvent.KEY_ENTER || keyCode == KeyEvent.KEY_KP_ENTER) return TerminalKey.ENTER;
        if (keyCode == KeyEvent.KEY_TAB) return TerminalKey.TAB;
        if (keyCode == KeyEvent.KEY_BACKSPACE) return TerminalKey.BACKSPACE;
        if (keyCode == KeyEvent.KEY_ESCAPE) return TerminalKey.ESCAPE;
        if (keyCode == KeyEvent.KEY_UP) return TerminalKey.UP;
        if (keyCode == KeyEvent.KEY_DOWN) return TerminalKey.DOWN;
        if (keyCode == KeyEvent.KEY_LEFT) return TerminalKey.LEFT;
        if (keyCode == KeyEvent.KEY_RIGHT) return TerminalKey.RIGHT;
        if (keyCode == KeyEvent.KEY_HOME) return TerminalKey.HOME;
        if (keyCode == KeyEvent.KEY_END) return TerminalKey.END;
        if (keyCode == KeyEvent.KEY_INSERT) return TerminalKey.INSERT;
        if (keyCode == KeyEvent.KEY_DELETE) return TerminalKey.DELETE;
        if (keyCode == KeyEvent.KEY_PAGE_UP) return TerminalKey.PAGE_UP;
        if (keyCode == KeyEvent.KEY_PAGE_DOWN) return TerminalKey.PAGE_DOWN;
        if (keyCode >= KeyEvent.KEY_F1 && keyCode <= KeyEvent.KEY_F12) {
            return TerminalKey.values()[TerminalKey.F1.ordinal() + keyCode - KeyEvent.KEY_F1];
        }
        return null;
    }

    private static ShapeDrawable colorDrawable(int color) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setColor(color);
        return drawable;
    }

    private final class InputCommitWatcher implements TextWatcher {
        @Override public void beforeTextChanged(CharSequence text, int start, int count, int after) {}
        @Override public void onTextChanged(CharSequence text, int start, int before, int count) {}

        @Override
        public void afterTextChanged(Editable text) {
            if (changingInput || text.length() == 0 || hasComposingSpan(text)) return;
            String committed = text.toString();
            changingInput = true;
            text.clear();
            changingInput = false;
            coordinator.sendText(committed);
        }

        private boolean hasComposingSpan(Editable text) {
            for (Object span : text.getSpans(0, text.length(), Object.class)) {
                if ((text.getSpanFlags(span) & Spanned.SPAN_COMPOSING) != 0) return true;
            }
            return false;
        }
    }
}
