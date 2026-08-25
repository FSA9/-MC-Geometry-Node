package com.mine.geometry_node.client.terminal.emulator;

import com.mine.geometry_node.client.terminal.TerminalSize;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/** Bounded VT/xterm screen model. Clipboard, hyperlinks, file transfer and other OSC/DCS effects are disabled. */
public final class VtTerminalEmulator {
    private static final int MAX_SCROLLBACK_LINES = 2_000;
    private static final int MAX_SCROLLBACK_CELLS = 100_000;
    private static final int MAX_CONTROL_SEQUENCE = 8_192;
    private static final int[] ANSI_COLORS = {
            0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510,
            0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
            0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543,
            0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF
    };

    private final Deque<TerminalCell[]> scrollback = new ArrayDeque<>();
    private final ByteArrayOutputStream replies = new ByteArrayOutputStream();
    private final StringBuilder sequence = new StringBuilder();

    private TerminalCell[][] screen;
    private TerminalCell[][] primaryScreen;
    private TerminalSize size;
    private TerminalStyle style = TerminalStyle.DEFAULT;
    private ParserState parserState = ParserState.GROUND;
    private int cursorRow;
    private int cursorColumn;
    private int savedRow;
    private int savedColumn;
    private int primarySavedRow;
    private int primarySavedColumn;
    private int scrollTop;
    private int scrollBottom;
    private boolean cursorVisible = true;
    private boolean wrapPending;
    private boolean autoWrap = true;
    private boolean applicationCursorKeys;
    private boolean bracketedPaste;
    private int mouseTrackingModes;
    private boolean sgrMouseMode;
    private boolean alternateScreen;
    private int utf8CodePoint;
    private int utf8Remaining;
    private int utf8Minimum;
    private long revision;

    public VtTerminalEmulator(TerminalSize initialSize) {
        size = initialSize;
        screen = blankScreen(initialSize);
        scrollBottom = initialSize.rows() - 1;
    }

    public synchronized void accept(byte[] bytes) {
        if (bytes.length > 0) revision++;
        for (byte value : bytes) acceptByte(value & 0xFF);
    }

    public synchronized void resize(TerminalSize newSize) {
        if (newSize.equals(size)) return;
        if (alternateScreen) {
            TerminalCell[][] alternate = screen;
            int alternateRow = cursorRow;
            int alternateColumn = cursorColumn;
            if (primaryScreen != null) {
                screen = primaryScreen;
                cursorRow = primarySavedRow;
                cursorColumn = primarySavedColumn;
                reflowPrimary(newSize);
                primaryScreen = screen;
                primarySavedRow = cursorRow;
                primarySavedColumn = cursorColumn;
            }
            int sourceStart = alternateResizeStart(alternate.length, newSize.rows(), alternateRow);
            screen = resizeScreen(alternate, newSize, sourceStart);
            cursorRow = clamp(alternateRow - sourceStart, 0, newSize.rows() - 1);
            cursorColumn = clamp(alternateColumn, 0, newSize.columns() - 1);
        } else {
            reflowPrimary(newSize);
        }
        size = newSize;
        cursorRow = clamp(cursorRow, 0, newSize.rows() - 1);
        cursorColumn = clamp(cursorColumn, 0, newSize.columns() - 1);
        scrollTop = 0;
        scrollBottom = newSize.rows() - 1;
        trimScrollback();
        revision++;
    }

    private void reflowPrimary(TerminalSize newSize) {
        List<TerminalCell[]> source = new ArrayList<>(scrollback.size() + screen.length);
        for (TerminalCell[] line : scrollback) source.add(copyLine(line));
        source.addAll(Arrays.asList(screen));
        int oldCursorLine = scrollback.size() + cursorRow;
        int sourceEnd = source.size();
        while (sourceEnd > oldCursorLine + 1 && lastContentColumn(source.get(sourceEnd - 1)) < 0) {
            sourceEnd--;
        }
        int reflowedCursorLine = 0;
        int reflowedCursorColumn = 0;
        List<TerminalCell[]> reflowed = new ArrayList<>();
        for (int index = 0; index < sourceEnd; index++) {
            TerminalCell[] line = source.get(index);
            int contentColumns = lastContentColumn(line) + 1;
            if (index == oldCursorLine) contentColumns = Math.max(contentColumns, cursorColumn + 1);
            contentColumns = Math.max(1, contentColumns);
            int before = reflowed.size();
            reflowed.addAll(reflowLine(line, contentColumns, newSize.columns()));
            if (index == oldCursorLine) {
                reflowedCursorLine = before + cursorColumn / newSize.columns();
                reflowedCursorColumn = cursorColumn % newSize.columns();
            }
        }

        int latestStart = Math.max(0, reflowed.size() - newSize.rows());
        int cursorStartMin = Math.max(0, reflowedCursorLine - newSize.rows() + 1);
        int cursorStartMax = Math.max(cursorStartMin, reflowedCursorLine);
        int screenStart = clamp(latestStart, cursorStartMin, cursorStartMax);
        scrollback.clear();
        for (int index = 0; index < screenStart; index++) scrollback.addLast(reflowed.get(index));
        screen = blankScreen(newSize);
        int screenLineCount = Math.min(newSize.rows(), reflowed.size() - screenStart);
        for (int row = 0; row < screenLineCount; row++) screen[row] = reflowed.get(screenStart + row);
        cursorRow = clamp(reflowedCursorLine - screenStart, 0, newSize.rows() - 1);
        cursorColumn = clamp(reflowedCursorColumn, 0, newSize.columns() - 1);
    }

    private List<TerminalCell[]> reflowLine(TerminalCell[] source, int contentColumns, int targetColumns) {
        List<TerminalCell[]> result = new ArrayList<>();
        TerminalCell[] target = blankLine(targetColumns, TerminalStyle.DEFAULT);
        int targetColumn = 0;
        for (int sourceColumn = 0; sourceColumn < Math.min(source.length, contentColumns); sourceColumn++) {
            TerminalCell cell = source[sourceColumn];
            if (cell.width() == 0) continue;
            int width = Math.max(1, cell.width());
            if ((width == 2 && targetColumn == targetColumns - 1) || targetColumn + width > targetColumns) {
                result.add(target);
                target = blankLine(targetColumns, TerminalStyle.DEFAULT);
                targetColumn = 0;
            }
            target[targetColumn] = cell;
            if (width == 2) target[targetColumn + 1] = TerminalCell.continuation(cell.style());
            targetColumn += width;
        }
        result.add(target);
        return result;
    }

    private static int lastContentColumn(TerminalCell[] line) {
        for (int column = line.length - 1; column >= 0; column--) {
            TerminalCell cell = line[column];
            if (cell.width() == 0 || !cell.text().equals(" ") || !cell.style().equals(TerminalStyle.DEFAULT)) return column;
        }
        return -1;
    }

    public synchronized TerminalSnapshot snapshot() {
        List<List<TerminalCell>> lines = new ArrayList<>(scrollback.size() + size.rows());
        if (!alternateScreen) {
            for (TerminalCell[] line : scrollback) lines.add(List.of(copyLine(line)));
        }
        for (TerminalCell[] line : screen) lines.add(List.of(copyLine(line)));
        int cursorLine = (alternateScreen ? 0 : scrollback.size()) + cursorRow;
        return new TerminalSnapshot(revision, size.columns(), size.rows(), lines, cursorLine, cursorColumn,
                cursorVisible, bracketedPaste, applicationCursorKeys, mouseTrackingModes != 0, sgrMouseMode,
                alternateScreen);
    }

    public synchronized long revision() { return revision; }

    public synchronized boolean applicationCursorKeys() { return applicationCursorKeys; }

    public synchronized boolean bracketedPaste() { return bracketedPaste; }

    public synchronized boolean mouseTracking() { return mouseTrackingModes != 0; }

    public synchronized boolean sgrMouseMode() { return sgrMouseMode; }

    public synchronized byte[] drainReplies() {
        byte[] value = replies.toByteArray();
        replies.reset();
        return value;
    }

    /** Restores a clean terminal for a new process run while preserving the current dimensions. */
    public synchronized void reset() {
        style = TerminalStyle.DEFAULT;
        scrollback.clear();
        replies.reset();
        sequence.setLength(0);
        screen = blankScreen(size);
        primaryScreen = null;
        cursorRow = cursorColumn = savedRow = savedColumn = 0;
        primarySavedRow = primarySavedColumn = 0;
        scrollTop = 0;
        scrollBottom = size.rows() - 1;
        cursorVisible = autoWrap = true;
        applicationCursorKeys = bracketedPaste = sgrMouseMode = alternateScreen = wrapPending = false;
        mouseTrackingModes = 0;
        parserState = ParserState.GROUND;
        utf8CodePoint = utf8Remaining = utf8Minimum = 0;
        revision++;
    }

    private void acceptByte(int value) {
        if (parserState == ParserState.GROUND && utf8Remaining > 0) {
            acceptUtf8Continuation(value);
            return;
        }
        switch (parserState) {
            case GROUND -> acceptGround(value);
            case ESCAPE -> acceptEscape(value);
            case CSI -> acceptCsi(value);
            case OSC -> acceptStringControl(value, ParserState.OSC_ESCAPE);
            case OSC_ESCAPE -> acceptStringEscape(value, ParserState.OSC);
            case DCS -> acceptStringControl(value, ParserState.DCS_ESCAPE);
            case DCS_ESCAPE -> acceptStringEscape(value, ParserState.DCS);
            case DISCARD_CSI -> {
                if (value >= 0x40 && value <= 0x7E) parserState = ParserState.GROUND;
                else if (value == 0x1B) parserState = ParserState.ESCAPE;
            }
            case DISCARD_STRING -> {
                if (value == 0x07) parserState = ParserState.GROUND;
                else if (value == 0x1B) parserState = ParserState.DISCARD_STRING_ESCAPE;
            }
            case DISCARD_STRING_ESCAPE -> parserState = value == '\\'
                    ? ParserState.GROUND : ParserState.DISCARD_STRING;
        }
    }

    private void acceptGround(int value) {
        switch (value) {
            case 0x00, 0x7F -> { }
            case 0x07 -> { }
            case 0x08 -> { cursorColumn = Math.max(0, cursorColumn - 1); wrapPending = false; }
            case 0x09 -> { cursorColumn = Math.min(size.columns() - 1, ((cursorColumn / 8) + 1) * 8); wrapPending = false; }
            case 0x0A, 0x0B, 0x0C -> lineFeed();
            case 0x0D -> { cursorColumn = 0; wrapPending = false; }
            case 0x1B -> { parserState = ParserState.ESCAPE; sequence.setLength(0); }
            default -> {
                if (value >= 0x20 && value < 0x80) putCodePoint(value);
                else if (value >= 0xC2 && value <= 0xF4) beginUtf8(value);
                else putCodePoint(0xFFFD);
            }
        }
    }

    private void beginUtf8(int first) {
        if (first < 0xE0) { utf8CodePoint = first & 0x1F; utf8Remaining = 1; utf8Minimum = 0x80; }
        else if (first < 0xF0) { utf8CodePoint = first & 0x0F; utf8Remaining = 2; utf8Minimum = 0x800; }
        else { utf8CodePoint = first & 0x07; utf8Remaining = 3; utf8Minimum = 0x10000; }
    }

    private void acceptUtf8Continuation(int value) {
        if ((value & 0xC0) != 0x80) {
            utf8Remaining = 0;
            putCodePoint(0xFFFD);
            acceptByte(value);
            return;
        }
        utf8CodePoint = (utf8CodePoint << 6) | (value & 0x3F);
        if (--utf8Remaining == 0) {
            int codePoint = utf8CodePoint;
            if (codePoint < utf8Minimum || codePoint > Character.MAX_CODE_POINT
                    || codePoint >= 0xD800 && codePoint <= 0xDFFF) codePoint = 0xFFFD;
            putCodePoint(codePoint);
        }
    }

    private void acceptEscape(int value) {
        parserState = ParserState.GROUND;
        switch (value) {
            case '[' -> { parserState = ParserState.CSI; sequence.setLength(0); }
            case ']' -> { parserState = ParserState.OSC; sequence.setLength(0); }
            case 'P', 'X', '^', '_' -> { parserState = ParserState.DCS; sequence.setLength(0); }
            case '7' -> saveCursor();
            case '8' -> restoreCursor();
            case 'D' -> lineFeed();
            case 'E' -> { cursorColumn = 0; lineFeed(); }
            case 'M' -> reverseIndex();
            case 'c' -> reset();
            default -> { }
        }
    }

    private void acceptCsi(int value) {
        if (value >= 0x40 && value <= 0x7E) {
            executeCsi((char) value, sequence.toString());
            sequence.setLength(0);
            parserState = ParserState.GROUND;
        } else if (value == 0x1B) {
            sequence.setLength(0);
            parserState = ParserState.ESCAPE;
        } else if (value >= 0x20 && value <= 0x3F && sequence.length() < MAX_CONTROL_SEQUENCE) {
            sequence.append((char) value);
        } else if (sequence.length() >= MAX_CONTROL_SEQUENCE) {
            sequence.setLength(0);
            parserState = ParserState.DISCARD_CSI;
        }
    }

    private void acceptStringControl(int value, ParserState escapeState) {
        if (value == 0x07) {
            parserState = ParserState.GROUND;
            sequence.setLength(0);
        } else if (value == 0x1B) {
            parserState = escapeState;
        } else if (sequence.length() < MAX_CONTROL_SEQUENCE) {
            sequence.append((char) value);
        } else {
            sequence.setLength(0);
            parserState = ParserState.DISCARD_STRING;
        }
    }

    private void acceptStringEscape(int value, ParserState stringState) {
        if (value == '\\') {
            parserState = ParserState.GROUND;
            sequence.setLength(0);
        } else {
            parserState = stringState;
            if (sequence.length() < MAX_CONTROL_SEQUENCE) sequence.append((char) 0x1B);
            acceptStringControl(value, stringState == ParserState.OSC ? ParserState.OSC_ESCAPE : ParserState.DCS_ESCAPE);
        }
    }

    private void executeCsi(char command, String raw) {
        boolean privateMode = raw.startsWith("?");
        String parameters = privateMode ? raw.substring(1) : raw;
        int[] values = parseParameters(parameters);
        int first = parameter(values, 0, 1);
        switch (command) {
            case 'A' -> moveCursor(-first, 0);
            case 'B', 'e' -> moveCursor(first, 0);
            case 'C', 'a' -> moveCursor(0, first);
            case 'D' -> moveCursor(0, -first);
            case 'E' -> { moveCursor(first, 0); cursorColumn = 0; }
            case 'F' -> { moveCursor(-first, 0); cursorColumn = 0; }
            case 'G', '`' -> setColumn(first - 1);
            case 'd' -> setRow(first - 1);
            case 'H', 'f' -> setCursor(parameter(values, 0, 1) - 1, parameter(values, 1, 1) - 1);
            case 'J' -> eraseDisplay(parameter(values, 0, 0));
            case 'K' -> eraseLine(parameter(values, 0, 0));
            case 'S' -> scrollUp(first);
            case 'T' -> scrollDown(first);
            case '@' -> insertCharacters(first);
            case 'P' -> deleteCharacters(first);
            case 'X' -> eraseCharacters(first);
            case 'L' -> insertLines(first);
            case 'M' -> deleteLines(first);
            case 'm' -> applySgr(values);
            case 'r' -> setScrollRegion(values);
            case 's' -> saveCursor();
            case 'u' -> restoreCursor();
            case 'h' -> setModes(values, privateMode, true);
            case 'l' -> setModes(values, privateMode, false);
            case 'n' -> respondStatus(values);
            case 'c' -> replies.writeBytes("\u001b[?1;2c".getBytes(StandardCharsets.US_ASCII));
            default -> { }
        }
    }

    private void putCodePoint(int codePoint) {
        if (isCombining(codePoint)) {
            int target = cursorColumn > 0 ? cursorColumn - 1 : cursorColumn;
            while (target > 0 && screen[cursorRow][target].width() == 0) target--;
            TerminalCell cell = screen[cursorRow][target];
            screen[cursorRow][target] = new TerminalCell(cell.text() + Character.toString(codePoint), cell.style(), cell.width());
            return;
        }
        if (wrapPending && autoWrap) {
            cursorColumn = 0;
            lineFeed();
        }
        int width = cellWidth(codePoint);
        if (width == 2 && cursorColumn == size.columns() - 1) {
            if (autoWrap) { cursorColumn = 0; lineFeed(); }
            else width = 1;
        }
        clearWideCellAt(cursorRow, cursorColumn);
        screen[cursorRow][cursorColumn] = new TerminalCell(Character.toString(codePoint), style, width);
        if (width == 2) screen[cursorRow][cursorColumn + 1] = TerminalCell.continuation(style);
        if (cursorColumn + width >= size.columns()) {
            cursorColumn = size.columns() - 1;
            wrapPending = true;
        } else {
            cursorColumn += width;
            wrapPending = false;
        }
    }

    private void lineFeed() {
        wrapPending = false;
        if (cursorRow == scrollBottom) scrollUp(1);
        else cursorRow = Math.min(size.rows() - 1, cursorRow + 1);
    }

    private void reverseIndex() {
        if (cursorRow == scrollTop) scrollDown(1);
        else cursorRow = Math.max(0, cursorRow - 1);
    }

    private void scrollUp(int count) {
        count = clamp(count, 1, scrollBottom - scrollTop + 1);
        for (int n = 0; n < count; n++) {
            TerminalCell[] removed = screen[scrollTop];
            // Inline TUIs such as Codex insert committed history through a top-anchored
            // subregion. Alternate-screen content must remain isolated from primary history.
            if (!alternateScreen && scrollTop == 0) addScrollback(removed);
            System.arraycopy(screen, scrollTop + 1, screen, scrollTop, scrollBottom - scrollTop);
            screen[scrollBottom] = blankLine();
        }
    }

    private void scrollDown(int count) {
        count = clamp(count, 1, scrollBottom - scrollTop + 1);
        for (int n = 0; n < count; n++) {
            System.arraycopy(screen, scrollTop, screen, scrollTop + 1, scrollBottom - scrollTop);
            screen[scrollTop] = blankLine();
        }
    }

    private void eraseDisplay(int mode) {
        if (mode == 2 || mode == 3) {
            for (int row = 0; row < size.rows(); row++) screen[row] = blankLine();
            if (mode == 3) scrollback.clear();
        } else if (mode == 1) {
            for (int row = 0; row < cursorRow; row++) screen[row] = blankLine();
            eraseWideRange(screen[cursorRow], 0, cursorColumn + 1);
        } else {
            eraseWideRange(screen[cursorRow], cursorColumn, size.columns());
            for (int row = cursorRow + 1; row < size.rows(); row++) screen[row] = blankLine();
        }
        normalizeWideCells(screen[cursorRow]);
        wrapPending = false;
    }

    private void eraseLine(int mode) {
        if (mode == 2) eraseWideRange(screen[cursorRow], 0, size.columns());
        else if (mode == 1) eraseWideRange(screen[cursorRow], 0, cursorColumn + 1);
        else eraseWideRange(screen[cursorRow], cursorColumn, size.columns());
        normalizeWideCells(screen[cursorRow]);
        wrapPending = false;
    }

    private void insertCharacters(int count) {
        TerminalCell[] line = screen[cursorRow];
        count = Math.min(count, size.columns() - cursorColumn);
        System.arraycopy(line, cursorColumn, line, cursorColumn + count, size.columns() - cursorColumn - count);
        eraseRange(line, cursorColumn, cursorColumn + count);
        normalizeWideCells(line);
    }

    private void deleteCharacters(int count) {
        TerminalCell[] line = screen[cursorRow];
        count = Math.min(count, size.columns() - cursorColumn);
        System.arraycopy(line, cursorColumn + count, line, cursorColumn, size.columns() - cursorColumn - count);
        eraseRange(line, size.columns() - count, size.columns());
        normalizeWideCells(line);
    }

    private void eraseCharacters(int count) {
        eraseWideRange(screen[cursorRow], cursorColumn, Math.min(size.columns(), cursorColumn + count));
        normalizeWideCells(screen[cursorRow]);
    }

    private void insertLines(int count) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return;
        count = Math.min(count, scrollBottom - cursorRow + 1);
        System.arraycopy(screen, cursorRow, screen, cursorRow + count, scrollBottom - cursorRow + 1 - count);
        for (int row = cursorRow; row < cursorRow + count; row++) screen[row] = blankLine();
    }

    private void deleteLines(int count) {
        if (cursorRow < scrollTop || cursorRow > scrollBottom) return;
        count = Math.min(count, scrollBottom - cursorRow + 1);
        System.arraycopy(screen, cursorRow + count, screen, cursorRow, scrollBottom - cursorRow + 1 - count);
        for (int row = scrollBottom - count + 1; row <= scrollBottom; row++) screen[row] = blankLine();
    }

    private void applySgr(int[] values) {
        if (values.length == 0) values = new int[]{0};
        int foreground = style.foreground();
        int background = style.background();
        boolean bold = style.bold();
        boolean underline = style.underline();
        boolean inverse = style.inverse();
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            if (value == 0) { foreground = TerminalStyle.DEFAULT_FOREGROUND; background = TerminalStyle.DEFAULT_BACKGROUND; bold = false; underline = false; inverse = false; }
            else if (value == 1) bold = true;
            else if (value == 4) underline = true;
            else if (value == 7) inverse = true;
            else if (value == 22) bold = false;
            else if (value == 24) underline = false;
            else if (value == 27) inverse = false;
            else if (value >= 30 && value <= 37) foreground = ANSI_COLORS[value - 30];
            else if (value >= 90 && value <= 97) foreground = ANSI_COLORS[value - 90 + 8];
            else if (value >= 40 && value <= 47) background = ANSI_COLORS[value - 40];
            else if (value >= 100 && value <= 107) background = ANSI_COLORS[value - 100 + 8];
            else if (value == 39) foreground = TerminalStyle.DEFAULT_FOREGROUND;
            else if (value == 49) background = TerminalStyle.DEFAULT_BACKGROUND;
            else if ((value == 38 || value == 48) && i + 1 < values.length) {
                int color;
                if (values[i + 1] == 5 && i + 2 < values.length) { color = palette256(values[i + 2]); i += 2; }
                else if (values[i + 1] == 2 && i + 4 < values.length) { color = rgb(values[i + 2], values[i + 3], values[i + 4]); i += 4; }
                else continue;
                if (value == 38) foreground = color; else background = color;
            }
        }
        style = new TerminalStyle(foreground, background, bold, underline, inverse);
    }

    private void setModes(int[] values, boolean privateMode, boolean enabled) {
        for (int value : values) {
            if (privateMode) {
                switch (value) {
                    case 1 -> applicationCursorKeys = enabled;
                    case 7 -> autoWrap = enabled;
                    case 25 -> cursorVisible = enabled;
                    case 47, 1047, 1049 -> setAlternateScreen(enabled);
                    case 1000, 1002, 1003 -> setMouseTrackingMode(value, enabled);
                    case 1006 -> sgrMouseMode = enabled;
                    case 2004 -> bracketedPaste = enabled;
                    default -> { }
                }
            }
        }
    }

    private void setMouseTrackingMode(int mode, boolean enabled) {
        int bit = 1 << (mode - 1000);
        if (enabled) mouseTrackingModes |= bit;
        else mouseTrackingModes &= ~bit;
    }

    private void setAlternateScreen(boolean enabled) {
        if (enabled == alternateScreen) return;
        if (enabled) {
            primaryScreen = screen;
            primarySavedRow = cursorRow;
            primarySavedColumn = cursorColumn;
            screen = blankScreen(size);
            cursorRow = cursorColumn = 0;
        } else if (primaryScreen != null) {
            screen = primaryScreen;
            primaryScreen = null;
            setCursor(primarySavedRow, primarySavedColumn);
        }
        alternateScreen = enabled;
        scrollTop = 0;
        scrollBottom = size.rows() - 1;
    }

    private void respondStatus(int[] values) {
        int value = parameter(values, 0, 0);
        if (value == 5) replies.writeBytes("\u001b[0n".getBytes(StandardCharsets.US_ASCII));
        else if (value == 6) replies.writeBytes(("\u001b[" + (cursorRow + 1) + ";" + (cursorColumn + 1) + "R")
                .getBytes(StandardCharsets.US_ASCII));
    }

    private void setScrollRegion(int[] values) {
        int top = parameter(values, 0, 1) - 1;
        int bottom = parameter(values, 1, size.rows()) - 1;
        if (top >= 0 && bottom < size.rows() && top < bottom) {
            scrollTop = top;
            scrollBottom = bottom;
            setCursor(0, 0);
        }
    }

    private void saveCursor() { savedRow = cursorRow; savedColumn = cursorColumn; }
    private void restoreCursor() { setCursor(savedRow, savedColumn); }
    private void moveCursor(int rows, int columns) { setCursor(cursorRow + rows, cursorColumn + columns); }
    private void setRow(int row) { setCursor(row, cursorColumn); }
    private void setColumn(int column) { setCursor(cursorRow, column); }
    private void setCursor(int row, int column) {
        cursorRow = clamp(row, 0, size.rows() - 1);
        cursorColumn = clamp(column, 0, size.columns() - 1);
        wrapPending = false;
    }

    private void addScrollback(TerminalCell[] line) {
        scrollback.addLast(copyLine(line));
        trimScrollback();
    }

    private void trimScrollback() {
        int maxLines = Math.min(MAX_SCROLLBACK_LINES, Math.max(1, MAX_SCROLLBACK_CELLS / size.columns()));
        while (scrollback.size() > maxLines) scrollback.removeFirst();
    }

    private TerminalCell[][] blankScreen(TerminalSize targetSize) {
        TerminalCell[][] result = new TerminalCell[targetSize.rows()][targetSize.columns()];
        for (int row = 0; row < targetSize.rows(); row++) {
            Arrays.fill(result[row], TerminalCell.blank(TerminalStyle.DEFAULT));
        }
        return result;
    }

    private TerminalCell[][] resizeScreen(TerminalCell[][] source, TerminalSize targetSize, int sourceStart) {
        TerminalCell[][] result = blankScreen(targetSize);
        int firstRow = clamp(sourceStart, 0, Math.max(0, source.length - 1));
        int rowCount = Math.min(source.length - firstRow, result.length);
        int columnCount = Math.min(source[0].length, result[0].length);
        for (int row = 0; row < rowCount; row++) {
            System.arraycopy(source[firstRow + row], 0, result[row], 0, columnCount);
            normalizeWideCells(result[row]);
        }
        return result;
    }

    private static int alternateResizeStart(int sourceRows, int targetRows, int cursorRow) {
        if (targetRows >= sourceRows) return 0;
        return clamp(cursorRow - targetRows + 1, 0, sourceRows - targetRows);
    }

    private TerminalCell[] blankLine() {
        return blankLine(size.columns(), style);
    }

    private static TerminalCell[] blankLine(int columns, TerminalStyle blankStyle) {
        TerminalCell[] line = new TerminalCell[columns];
        Arrays.fill(line, TerminalCell.blank(blankStyle));
        return line;
    }

    private void eraseRange(TerminalCell[] line, int from, int to) {
        Arrays.fill(line, Math.max(0, from), Math.min(line.length, to), TerminalCell.blank(style));
    }

    private void eraseWideRange(TerminalCell[] line, int from, int to) {
        int start = clamp(from, 0, line.length);
        int end = clamp(to, start, line.length);
        if (start > 0 && start < line.length && line[start].width() == 0 && line[start - 1].width() == 2) start--;
        if (end > 0 && end < line.length && line[end].width() == 0 && line[end - 1].width() == 2) end++;
        eraseRange(line, start, end);
    }

    private void clearWideCellAt(int row, int column) {
        TerminalCell[] line = screen[row];
        if (line[column].width() == 0 && column > 0) line[column - 1] = TerminalCell.blank(style);
        if (line[column].width() == 2 && column + 1 < line.length) line[column + 1] = TerminalCell.blank(style);
    }

    private void normalizeWideCells(TerminalCell[] line) {
        for (int column = 0; column < line.length; column++) {
            TerminalCell cell = line[column];
            if (cell.width() == 2) {
                if (column + 1 >= line.length) line[column] = TerminalCell.blank(style);
                else { line[column + 1] = TerminalCell.continuation(cell.style()); column++; }
            } else if (cell.width() == 0 && (column == 0 || line[column - 1].width() != 2)) {
                line[column] = TerminalCell.blank(style);
            }
        }
    }

    private static TerminalCell[] copyLine(TerminalCell[] line) { return line.clone(); }

    private static int[] parseParameters(String raw) {
        int intermediate = raw.indexOf(' ');
        if (intermediate >= 0) raw = raw.substring(0, intermediate);
        if (raw.isEmpty()) return new int[0];
        String[] parts = raw.split(";", -1);
        int[] values = new int[Math.min(parts.length, 32)];
        for (int i = 0; i < values.length; i++) {
            try { values[i] = parts[i].isEmpty() ? -1 : Math.min(65_535, Integer.parseInt(parts[i])); }
            catch (NumberFormatException ignored) { values[i] = -1; }
        }
        return values;
    }

    private static int parameter(int[] values, int index, int fallback) {
        return index >= values.length || values[index] <= 0 ? fallback : values[index];
    }

    private static int palette256(int index) {
        index = clamp(index, 0, 255);
        if (index < 16) return ANSI_COLORS[index];
        if (index < 232) {
            int value = index - 16;
            int red = value / 36;
            int green = value / 6 % 6;
            int blue = value % 6;
            return rgb(red == 0 ? 0 : 55 + red * 40, green == 0 ? 0 : 55 + green * 40, blue == 0 ? 0 : 55 + blue * 40);
        }
        int gray = 8 + (index - 232) * 10;
        return rgb(gray, gray, gray);
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | clamp(red, 0, 255) << 16 | clamp(green, 0, 255) << 8 | clamp(blue, 0, 255);
    }

    private static boolean isCombining(int codePoint) {
        int type = Character.getType(codePoint);
        return type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK || codePoint == 0x200D || codePoint >= 0xFE00 && codePoint <= 0xFE0F;
    }

    static int cellWidth(int codePoint) {
        if (isCombining(codePoint)) return 0;
        return codePoint >= 0x1100 && (codePoint <= 0x115F || codePoint == 0x2329 || codePoint == 0x232A
                || codePoint >= 0x2E80 && codePoint <= 0xA4CF && codePoint != 0x303F
                || codePoint >= 0xAC00 && codePoint <= 0xD7A3
                || codePoint >= 0xF900 && codePoint <= 0xFAFF
                || codePoint >= 0xFE10 && codePoint <= 0xFE6F
                || codePoint >= 0xFF00 && codePoint <= 0xFF60
                || codePoint >= 0xFFE0 && codePoint <= 0xFFE6
                || codePoint >= 0x1F300 && codePoint <= 0x1FAFF
                || codePoint >= 0x20000 && codePoint <= 0x3FFFD) ? 2 : 1;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private enum ParserState {
        GROUND, ESCAPE, CSI, OSC, OSC_ESCAPE, DCS, DCS_ESCAPE,
        DISCARD_CSI, DISCARD_STRING, DISCARD_STRING_ESCAPE
    }
}
