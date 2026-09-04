package com.mine.geometry_node.core.engine.blueprint.event;

import java.util.ArrayList;
import java.util.List;

/** Shared player-input identifiers and their stable network-mask ordering. */
public final class PlayerInputKeys {
    public static final String SPACE = "space";
    public static final String TAB = "tab";
    public static final String ENTER = "enter";
    public static final String CTRL = "ctrl";
    public static final String SHIFT = "shift";
    public static final String ALT = "alt";

    private static final int SKILL_COUNT = 12;
    public static final List<String> DIRECT_KEYS = List.of(SPACE, TAB, ENTER);
    public static final List<String> MODIFIER_KEYS = List.of(CTRL, SHIFT, ALT);
    public static final List<String> ALL_KEYS = createAllKeys();

    private PlayerInputKeys() {
    }

    public static int skillCount() {
        return SKILL_COUNT;
    }

    public static int skillOffset() {
        return DIRECT_KEYS.size() + MODIFIER_KEYS.size();
    }

    public static int trackedCount() {
        return ALL_KEYS.size();
    }

    public static String[] options() {
        return ALL_KEYS.toArray(String[]::new);
    }

    public static int indexOf(String keyId) {
        return ALL_KEYS.indexOf(keyId);
    }

    public static int maskOf(String keyId) {
        int index = indexOf(keyId);
        return index >= 0 ? 1 << index : 0;
    }

    public static int allMask() {
        return (1 << trackedCount()) - 1;
    }

    public static String skillId(int zeroBasedIndex) {
        if (zeroBasedIndex < 0 || zeroBasedIndex >= SKILL_COUNT) {
            throw new IndexOutOfBoundsException("Invalid skill index: " + zeroBasedIndex);
        }
        return "skill_" + (zeroBasedIndex + 1);
    }

    private static List<String> createAllKeys() {
        List<String> keys = new ArrayList<>(DIRECT_KEYS.size() + MODIFIER_KEYS.size() + SKILL_COUNT);
        keys.addAll(DIRECT_KEYS);
        keys.addAll(MODIFIER_KEYS);
        for (int i = 0; i < SKILL_COUNT; i++) {
            keys.add(skillId(i));
        }
        return List.copyOf(keys);
    }
}
