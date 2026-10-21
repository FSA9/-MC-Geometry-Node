package com.mine.geometry_node.client.ui.theme;

import java.util.Objects;

/** Immutable semantic colors shared by every preset. Components translate these values into styles.
 * Preset selection belongs to the caller; this model has no global active theme or view dependencies. */
public record UiTheme(Surface surface,
                      Text text,
                      Border border,
                      Selection selection,
                      Control control,
                      Status status,
                      Scroll scroll,
                      Graph graph,
                      Decoration decoration,
                      Buttons buttons) {
    public UiTheme {
        Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(border, "border");
        Objects.requireNonNull(selection, "selection");
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scroll, "scroll");
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(decoration, "decoration");
        Objects.requireNonNull(buttons, "buttons");
    }

    public record Surface(int canvas,
                         int root,
                         int panel,
                         int header,
                         int sidebar,
                         int node,
                         int overlayDim,
                         int popup,
                         int modalTitle,
                         int modalScrim,
                         int modalScrimLight,
                         int tooltip,
                         int hover) {}

    public record Text(int strong,
                         int normal,
                         int secondary,
                         int muted,
                         int hint,
                         int disabled) {}

    public record Border(int strong,
                         int subtle,
                         int divider,
                         int focus) {}

    public record Selection(int fill,
                         int hover,
                         int pressed,
                         int subtle,
                         int subtleHover,
                         int subtlePressed,
                         int outline,
                         int marqueeFill,
                         int text) {}

    public record Control(int input,
                         int inputBorder,
                         int icon,
                         int iconHover,
                         int iconPressed,
                         int iconBorder,
                         int iconDisabledBorder,
                         int hoverOverlay,
                         int pressedOverlay,
                         int rangeFill,
                         int disabledBackground) {}

    public record Status(int info,
                         int success,
                         int warning,
                         int error,
                         int errorBackground,
                         int errorBorder) {}

    public record Scroll(int thumb,
                         int track,
                         int edge) {}

    public record Graph(int grid,
                         int gridMajor,
                         int axis,
                         int nodeOutline,
                         int draftConnection) {}

    public record Decoration(int favorite,
                         int remote,
                         int remoteSurface,
                         int remoteHover,
                         int remotePressed,
                         int remoteSelected,
                         int checkerLight,
                         int checkerDark) {}

    public record ButtonColors(int normal, int hover, int pressed, int text) {}

    public record Buttons(ButtonColors primary, ButtonColors secondary, ButtonColors inline,
                          ButtonColors danger, ButtonColors quiet) {
        public Buttons {
            Objects.requireNonNull(primary, "primary");
            Objects.requireNonNull(secondary, "secondary");
            Objects.requireNonNull(inline, "inline");
            Objects.requireNonNull(danger, "danger");
            Objects.requireNonNull(quiet, "quiet");
        }
    }

    /** Replaces the alpha channel; does not multiply an existing surface opacity. */
    public static int withAlpha(int color, int alpha) {
        if (alpha < 0 || alpha > 255) throw new IllegalArgumentException("Alpha must be in [0, 255]");
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
