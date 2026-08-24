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
import com.mine.geometry_node.client.agent.mcp.McpToolEvent;
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
import java.util.concurrent.atomic.AtomicLong;

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
    private final TextView trustedEventView;
    private final float cellWidthPx;
    private final int cellHeightPx;
    private final AtomicBoolean refreshQueued = new AtomicBoolean();
    private final AtomicBoolean dirty = new AtomicBoolean();
    private final AtomicLong startupGeneration = new AtomicLong();

    private volatile TerminalRunState displayedState = TerminalRunState.IDLE;
    private volatile String pendingError = "";
    private volatile TerminalSize pendingSize = new TerminalSize(80, 24);
    private TerminalSize appliedSize = new TerminalSize(80, 24);
    private boolean changingInput;
    private boolean followOutput = true;
    private boolean hasGuiScrollback;
    private volatile boolean startRequested;
    private volatile boolean disposed;
    private final ShellStartAction shellStartAction;

    @FunctionalInterface
    public interface ShellStartAction {
        void start(TerminalSize size, long generation);
    }

    public ShellTerminalView(Context context, ShellTerminalCoordinator coordinator) {
        this(context, coordinator, null);
    }

    public ShellTerminalView(Context context, ShellTerminalCoordinator coordinator,
                             ShellStartAction shellStartAction) {
        super(context);
        this.coordinator = coordinator;
        this.shellStartAction = shellStartAction;
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

        trustedEventView = UIUtils.createLockedTextView(context, "MCP  starts with PowerShell", 11f, 0xFFB5CEA8);
        trustedEventView.setGravity(Gravity.CENTER_VERTICAL);
        trustedEventView.setPadding(UIUtils.dp2pxInt(10), 0, UIUtils.dp2pxInt(8), 0);
        trustedEventView.setBackground(colorDrawable(0xFF173A2A));
        trustedEventView.setVisibility(View.VISIBLE);
        addView(trustedEventView, new LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, UIUtils.dp2pxInt(STATUS_HEIGHT_DP)));

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
        terminalSurface.setOnGenericMotionListener(this::handleTerminalScroll);
        scrollView.setOnGenericMotionListener(this::handleTerminalScroll);
        screenView.setOnGenericMotionListener(this::handleTerminalScroll);
        setOnGenericMotionListener(this::handleTerminalScroll);
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
        followOutput = true;
        startRequested = true;
        pendingError = "";
        if (shellStartAction == null) {
            reportError("PowerShell starter is unavailable");
            return;
        }
        shellStartAction.start(pendingSize, startupGeneration.incrementAndGet());
        requestRefresh();
    }

    public void reportError(String message) { onError(message); }

    public boolean isStartPending() {
        return startRequested && !displayedState.hasActiveBackend();
    }

    public boolean acceptsShellStart(long generation) {
        return !disposed && startRequested && startupGeneration.get() == generation;
    }

    public void onTrustedToolEvent(McpToolEvent event) {
        if (disposed || event == null) return;
        post(() -> {
            if (disposed) return;
            String code = event.code().isBlank() ? "" : "  " + event.code();
            trustedEventView.setText("MCP  " + event.toolName() + "  " + event.state() + code);
            trustedEventView.setTextColor(event.state() == McpToolEvent.State.FAILED ? 0xFFF48771 : 0xFFB5CEA8);
        });
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
        int statusHeight = STATUS_HEIGHT_DP * 2;
        int usableHeight = Math.max(1, height - UIUtils.dp2pxInt(statusHeight + 12));
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
            post(() -> {
                if (!disposed) {
                    trustedEventView.setText("MCP  http://127.0.0.1:37654/mcp");
                    trustedEventView.setTextColor(0xFFB5CEA8);
                }
            });
            post(this::requestInputFocus);
        }
        if (state == TerminalRunState.EXITED || state == TerminalRunState.FAILED) {
            startRequested = false;
            post(() -> {
                if (!disposed) trustedEventView.setText("MCP  stopped");
            });
        }
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
        boolean followBottom = followOutput;
        hasGuiScrollback = snapshot.lines().size() > snapshot.rows();
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
            scrollView.post(() -> {
                if (!disposed && followOutput) scrollView.scrollTo(0, maxScrollY());
            });
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
        if (event.getActionMasked() == MotionEvent.ACTION_UP) {
            followOutput = isNearBottom();
            requestInputFocus();
        }
        return false;
    }

    private boolean handleTerminalScroll(View view, MotionEvent event) {
        if (event.getAction() != MotionEvent.ACTION_SCROLL) return false;
        float amount = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
        if (amount == 0.0f) return false;

        int maxScroll = maxScrollY();
        if (hasGuiScrollback && maxScroll > 0) {
            int distance = Math.max(cellHeightPx, Math.round(Math.abs(amount) * cellHeightPx * 3.0f));
            int direction = amount > 0.0f ? -1 : 1;
            int target = Math.max(0, Math.min(maxScroll, scrollView.getScrollY() + direction * distance));
            scrollView.scrollTo(0, target);
            followOutput = target >= maxScroll - cellHeightPx;
        } else if (displayedState.hasActiveBackend()) {
            boolean up = amount > 0.0f;
            int column = Math.max(1, Math.min(pendingSize.columns(),
                    1 + Math.round((event.getX() - UIUtils.dp2px(8f)) / cellWidthPx)));
            int row = Math.max(1, Math.min(pendingSize.rows(),
                    1 + Math.round((event.getY() - UIUtils.dp2px(6f)) / cellHeightPx)));
            if (!coordinator.sendMouseWheel(up, column, row)) {
                coordinator.sendKey(up ? TerminalKey.PAGE_UP : TerminalKey.PAGE_DOWN);
            }
        }
        return true;
    }

    private int maxScrollY() {
        return Math.max(0, screenView.getHeight() - scrollView.getHeight());
    }

    private boolean isNearBottom() {
        return scrollView.getScrollY() >= maxScrollY() - cellHeightPx;
    }

    private void toggleProcess() {
        if (displayedState.hasActiveBackend()) {
            coordinator.stop();
        } else if (startRequested) {
            startupGeneration.incrementAndGet();
            startRequested = false;
            pendingError = "启动已取消";
            requestRefresh();
        } else {
            ensureStarted();
        }
    }

    private void updateStatus() {
        String detail = pendingError.isBlank() ? displayedState.name() : pendingError;
        statusView.setText("PowerShell  " + detail);
        actionView.setText(displayedState.hasActiveBackend() ? "Stop" : startRequested ? "Cancel" : "Start");
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
