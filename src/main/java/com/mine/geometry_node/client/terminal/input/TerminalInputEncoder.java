package com.mine.geometry_node.client.terminal.input;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Encodes keyboard, IME commits and bounded clipboard paste as xterm-compatible input bytes. */
public final class TerminalInputEncoder {
    public static final int MAX_PASTE_BYTES = 1024 * 1024;

    private TerminalInputEncoder() {}

    public static byte[] text(String text) {
        Objects.requireNonNull(text, "text");
        return text.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] paste(String text, boolean bracketedPaste) {
        Objects.requireNonNull(text, "text");
        byte[] content = text.getBytes(StandardCharsets.UTF_8);
        if (content.length > MAX_PASTE_BYTES) {
            throw new IllegalArgumentException("Paste exceeds " + MAX_PASTE_BYTES + " UTF-8 bytes");
        }
        if (!bracketedPaste) return content;
        byte[] prefix = "\u001b[200~".getBytes(StandardCharsets.US_ASCII);
        byte[] suffix = "\u001b[201~".getBytes(StandardCharsets.US_ASCII);
        byte[] result = new byte[prefix.length + content.length + suffix.length];
        System.arraycopy(prefix, 0, result, 0, prefix.length);
        System.arraycopy(content, 0, result, prefix.length, content.length);
        System.arraycopy(suffix, 0, result, prefix.length + content.length, suffix.length);
        return result;
    }

    public static byte[] key(TerminalKey key, boolean applicationCursorKeys) {
        Objects.requireNonNull(key, "key");
        String sequence = switch (key) {
            case ENTER -> "\r";
            case TAB -> "\t";
            case BACKSPACE -> "\u007f";
            case ESCAPE -> "\u001b";
            case UP -> cursor('A', applicationCursorKeys);
            case DOWN -> cursor('B', applicationCursorKeys);
            case RIGHT -> cursor('C', applicationCursorKeys);
            case LEFT -> cursor('D', applicationCursorKeys);
            case HOME -> applicationCursorKeys ? "\u001bOH" : "\u001b[H";
            case END -> applicationCursorKeys ? "\u001bOF" : "\u001b[F";
            case INSERT -> "\u001b[2~";
            case DELETE -> "\u001b[3~";
            case PAGE_UP -> "\u001b[5~";
            case PAGE_DOWN -> "\u001b[6~";
            case F1 -> "\u001bOP";
            case F2 -> "\u001bOQ";
            case F3 -> "\u001bOR";
            case F4 -> "\u001bOS";
            case F5 -> "\u001b[15~";
            case F6 -> "\u001b[17~";
            case F7 -> "\u001b[18~";
            case F8 -> "\u001b[19~";
            case F9 -> "\u001b[20~";
            case F10 -> "\u001b[21~";
            case F11 -> "\u001b[23~";
            case F12 -> "\u001b[24~";
        };
        return sequence.getBytes(StandardCharsets.US_ASCII);
    }

    public static byte[] control(char character) {
        char upper = Character.toUpperCase(character);
        if (upper >= '@' && upper <= '_') return new byte[]{(byte) (upper - '@')};
        if (upper == '?') return new byte[]{0x7F};
        throw new IllegalArgumentException("Unsupported control character: " + character);
    }

    public static byte[] mouseWheel(boolean up, int column, int row, boolean sgrMode) {
        int normalizedColumn = Math.max(1, column);
        int normalizedRow = Math.max(1, row);
        int button = up ? 64 : 65;
        if (sgrMode) {
            return ("\u001b[<" + button + ";" + normalizedColumn + ";" + normalizedRow + "M")
                    .getBytes(StandardCharsets.US_ASCII);
        }
        int legacyColumn = Math.min(223, normalizedColumn);
        int legacyRow = Math.min(223, normalizedRow);
        return new byte[]{0x1B, '[', 'M', (byte) (button + 32),
                (byte) (legacyColumn + 32), (byte) (legacyRow + 32)};
    }

    private static String cursor(char suffix, boolean application) {
        return application ? "\u001bO" + suffix : "\u001b[" + suffix;
    }
}
