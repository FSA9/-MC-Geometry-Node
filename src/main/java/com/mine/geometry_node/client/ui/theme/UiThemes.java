package com.mine.geometry_node.client.ui.theme;

/** Sole preset entry point. Both editor and HUD are values of the same immutable theme model. */
public final class UiThemes {
    private UiThemes() {}

    public static final UiTheme EDITOR = new UiTheme(
            new UiTheme.Surface(
                    0xFF181818, // canvas
                    0xFF1D1D1D, // root
                    0xFF252525, // panel
                    0xFF2D2D2D, // header
                    0xFF303030, // sidebar
                    0xE6303030, // node
                    0x99000000, // overlayDim
                    0xFF252525, // popup
                    0xFF202020, // modalTitle
                    0x66000000, // modalScrim
                    0x33000000, // modalScrimLight
                    0xEE202020, // tooltip
                    0xFF484848), // hover
            new UiTheme.Text(
                    0xFFFFFFFF, // strong
                    0xFFE6E6E6, // normal
                    0xFFCCCCCC, // secondary
                    0xFFAAAAAA, // muted
                    0xFF737B86, // hint
                    0xFF999999), // disabled
            new UiTheme.Border(
                    0xFF5C5C5C, // strong
                    0xFF3A3A3A, // subtle
                    0xFF484848, // divider
                    0xFF6FA2DD), // focus
            new UiTheme.Selection(
                    0xFF3D6EA8, // fill
                    0xFF4A7FB9, // hover
                    0xFF315C8E, // pressed
                    0xFF3A3A3A, // subtle
                    0xFF484848, // subtleHover
                    0xFF303030, // subtlePressed
                    0xFF6FA2DD, // outline
                    0x336FA2DD, // marqueeFill
                    0x806FA2DD), // text
            new UiTheme.Control(
                    0xFF202020, // input
                    0xFF484848, // inputBorder
                    0xFF3B3B3B, // icon
                    0xFF484848, // iconHover
                    0xFF303030, // iconPressed
                    0xFF444444, // iconBorder
                    0xFF383838, // iconDisabledBorder
                    0x18FFFFFF, // hoverOverlay
                    0x28000000, // pressedOverlay
                    0x886FA2DD, // rangeFill
                    0xFF252525), // disabledBackground
            new UiTheme.Status(
                    0xFF8DBAF0, // info
                    0xFF8AC998, // success
                    0xFFE3BD75, // warning
                    0xFFE08080, // error
                    0xFF5A3030, // errorBackground
                    0xFFB85C5C), // errorBorder
            new UiTheme.Scroll(
                    0x84FFFFFF, // thumb
                    0x39FFFFFF, // track
                    0x806FA2DD), // edge
            new UiTheme.Graph(
                    0xFF282828, // grid
                    0xFF303030, // gridMajor
                    0xFF000000, // axis
                    0xFF111111, // nodeOutline
                    0xFFE6E6E6), // draftConnection
            new UiTheme.Decoration(
                    0xFFFFD166, // favorite
                    0xFF9CC6FF, // remote
                    0xFF263445, // remoteSurface
                    0xFF2F4055, // remoteHover
                    0xFF3C5874, // remotePressed
                    0xFF36597A, // remoteSelected
                    0xFFCCCCCC, // checkerLight
                    0xFF888888), // checkerDark
            new UiTheme.Buttons(
                    new UiTheme.ButtonColors(0xFF2F7DDE,0xFF3D8BEA,0xFF2768BA,0xFFFFFFFF), // primary
                    new UiTheme.ButtonColors(0xFF454545,0xFF525252,0xFF383838,0xFFD8D8D8), // secondary
                    new UiTheme.ButtonColors(0xFF30343B,0xFF3A4049,0xFF292D33,0xFFBFC7D5), // inline
                    new UiTheme.ButtonColors(0xFF9A3D3D,0xFFAC4A4A,0xFF7C3030,0xFFFFFFFF), // danger
                    new UiTheme.ButtonColors(0,0xFF484848,0xFF303030,0xFFCCCCCC))); // quiet

    public static final UiTheme HUD = new UiTheme(
            new UiTheme.Surface(
                    0xF2111315, // canvas
                    0xF2111315, // root
                    0xD91A1D1F, // panel
                    0xF0222729, // header
                    0xD91A1D1F, // sidebar
                    0xD91A1D1F, // node
                    0x78000000, // overlayDim
                    0xF2111315, // popup
                    0xF2111315, // modalTitle
                    0x78000000, // modalScrim
                    0x40000000, // modalScrimLight
                    0xF2111315, // tooltip
                    0xF0222729), // hover
            new UiTheme.Text(
                    0xFFF0EEE7, // strong
                    0xFFF0EEE7, // normal
                    0xFFD8D3C8, // secondary
                    0xFF8F97A5, // muted
                    0xFF8F97A5, // hint
                    0xFF8F97A5), // disabled
            new UiTheme.Border(
                    0xFFD5B46A, // strong
                    0x55D5B46A, // subtle
                    0x55D5B46A, // divider
                    0xFFD5B46A), // focus
            new UiTheme.Selection(
                    0xFF594A2C, // fill
                    0xFF6C5935, // hover
                    0xFF453922, // pressed
                    0xFF33302A, // subtle
                    0xFF454035, // subtleHover
                    0xFF28251F, // subtlePressed
                    0xFFD5B46A, // outline
                    0x33D5B46A, // marqueeFill
                    0x80D5B46A), // text
            new UiTheme.Control(
                    0xD91A1D1F, // input
                    0x55D5B46A, // inputBorder
                    0xFF292D2F, // icon
                    0xFF353A3D, // iconHover
                    0xFF202426, // iconPressed
                    0x55D5B46A, // iconBorder
                    0x288F97A5, // iconDisabledBorder
                    0x18FFFFFF, // hoverOverlay
                    0x28000000, // pressedOverlay
                    0x88D5B46A, // rangeFill
                    0xFF202325), // disabledBackground
            new UiTheme.Status(
                    0xFFD5B46A, // info
                    0xFF72C58D, // success
                    0xFFE2B565, // warning
                    0xFFEA7C73, // error
                    0xFF50302C, // errorBackground
                    0xFFEA7C73), // errorBorder
            new UiTheme.Scroll(
                    0x84D5B46A, // thumb
                    0x39D5B46A, // track
                    0x80D5B46A), // edge
            EDITOR.graph(),
            EDITOR.decoration(),
            new UiTheme.Buttons(
                    new UiTheme.ButtonColors(0xFFD5B46A, 0xFFE1C37C, 0xFFB79A55, 0xFF111315), // primary
                    new UiTheme.ButtonColors(0xFF292D2F, 0xFF353A3D, 0xFF202426, 0xFFF0EEE7), // secondary
                    new UiTheme.ButtonColors(0xFF292D2F, 0xFF353A3D, 0xFF202426, 0xFFF0EEE7), // inline
                    new UiTheme.ButtonColors(0xFF9A3D3D, 0xFFAC4A4A, 0xFF7C3030, 0xFFF0EEE7), // danger
                    new UiTheme.ButtonColors(0, 0xFF353A3D, 0xFF202426, 0xFFF0EEE7))); // quiet

}
