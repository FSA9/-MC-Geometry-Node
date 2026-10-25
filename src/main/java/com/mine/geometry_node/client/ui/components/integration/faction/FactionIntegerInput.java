package com.mine.geometry_node.client.ui.components.integration.faction;

import com.mine.geometry_node.client.ui.components.base.input.UiTextInput;
import com.mine.geometry_node.client.ui.theme.UiTheme;
import icyllis.modernui.core.Context;
import java.util.function.IntConsumer;
import static com.mine.geometry_node.client.ui.components.integration.faction.FactionUi.tr;

/** Integer-only, spinner-free editor. Incomplete text belongs to the open presentation. */
public final class FactionIntegerInput extends UiTextInput {
    private final int minimum, maximum;
    private final IntConsumer submit;
    private int confirmed;
    private String baseline;
    private boolean editing = true;

    public FactionIntegerInput(Context context, UiTheme theme, int value, int minimum, int maximum,
                               IntConsumer submit) {
        super(context, Style.forTheme(theme));
        this.minimum = minimum; this.maximum = maximum; this.submit = submit; confirmed = value;
        baseline = Integer.toString(value); setSingleLine(true); setText(baseline);
        setEditActions(this::commit, () -> { discard(); clearFocus(); });
        setOnFocusChangeListener((view, focused) -> { if (!focused && editing) commit(); });
    }

    public void refresh(int value) {
        // Authority advances even while an incomplete edit keeps its own raw-text baseline.
        boolean pending = hasPendingChanges(); confirmed = value;
        if (pending) return;
        baseline = Integer.toString(value); setText(baseline); setErrorMessage(null);
    }

    public void setEditable(boolean enabled) { editing = enabled; setEnabled(enabled); }
    public boolean hasPendingChanges() { return !getText().toString().equals(baseline); }
    public void discard() { baseline = Integer.toString(confirmed); setText(baseline); setErrorMessage(null); }
    public void dispose() { editing = false; setEnabled(false); }
    private void commit() {
        if (!editing || !isEnabled()) return;
        try {
            int value = Integer.parseInt(getText().toString().trim());
            if (value < minimum || value > maximum) throw new IllegalArgumentException();
            if (value != confirmed) {
                int previous = confirmed; String previousBaseline = baseline;
                confirmed = value; baseline = Integer.toString(value); setText(baseline);
                try { submit.accept(value); }
                catch (RuntimeException failed) { confirmed = previous; baseline = previousBaseline; throw failed; }
            } else { baseline = Integer.toString(value); setText(baseline); }
            setErrorMessage(null);
        } catch (IllegalArgumentException invalid) { setErrorMessage(tr("invalid_integer", minimum, maximum)); }
    }
}
