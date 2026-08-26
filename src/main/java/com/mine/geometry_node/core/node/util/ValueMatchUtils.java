package com.mine.geometry_node.core.node.util;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public final class ValueMatchUtils {
    public static final String MODE_TYPE_ONLY = "type_only";
    public static final String MODE_REGISTRY_ID = "registry_id";
    public static final String MODE_COMPONENTS = "components";
    public static final String MODE_EXACT = "exact";
    public static final String[] MODE_OPTIONS = {MODE_TYPE_ONLY, MODE_REGISTRY_ID, MODE_COMPONENTS, MODE_EXACT};

    public static final String COUNT_IGNORE = "ignore_count";
    public static final String COUNT_EXACT = "exact_count";
    public static final String COUNT_AT_LEAST = "at_least";
    public static final String COUNT_AT_MOST = "at_most";
    public static final String[] COUNT_OPTIONS = {COUNT_IGNORE, COUNT_EXACT, COUNT_AT_LEAST, COUNT_AT_MOST};

    private ValueMatchUtils() {
    }

    public static boolean valuesEqual(Object a, Object b, String rawMode, String rawCountMode,
                                      GraphDataContext context) {
        a = ValueTagUtils.unwrap(a);
        b = ValueTagUtils.unwrap(b);

        if (a == null && b == null) return true;
        if (a == null || b == null) return false;

        String mode = normalizeMode(rawMode);
        boolean baseMatch = switch (mode) {
            case MODE_TYPE_ONLY -> typeOnlyEqual(a, b, context);
            case MODE_REGISTRY_ID -> registryIdEqual(a, b, context);
            case MODE_EXACT -> exactEqual(a, b);
            default -> componentsEqual(a, b, context);
        };
        if (!baseMatch) {
            return false;
        }

        return countMatches(a, b, normalizeCountMode(rawCountMode), mode);
    }

    public static boolean itemStackMatches(ItemStack candidate, ItemStack template, String rawMode,
                                           GraphDataContext context) {
        return itemStackMatches(candidate, template, rawMode, COUNT_IGNORE, context);
    }

    public static boolean itemStackMatches(ItemStack candidate, ItemStack template,
                                           String rawMode, String rawCountMode,
                                           GraphDataContext context) {
        if (candidate == null || candidate.isEmpty() || template == null || template.isEmpty()) {
            return false;
        }
        return valuesEqual(candidate, template, rawMode, rawCountMode, context);
    }

    public static String normalizeMode(String rawMode) {
        if (rawMode == null) {
            return MODE_COMPONENTS;
        }
        String mode = rawMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case MODE_TYPE_ONLY, MODE_REGISTRY_ID, MODE_EXACT -> mode;
            default -> MODE_COMPONENTS;
        };
    }

    public static String normalizeCountMode(String rawCountMode) {
        if (rawCountMode == null) {
            return COUNT_IGNORE;
        }
        String mode = rawCountMode.trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case COUNT_EXACT, COUNT_AT_LEAST, COUNT_AT_MOST -> mode;
            default -> COUNT_IGNORE;
        };
    }

    private static boolean typeOnlyEqual(Object a, Object b, GraphDataContext context) {
        Set<String> left = ValueTagUtils.kindKeys(a, context);
        Set<String> right = ValueTagUtils.kindKeys(b, context);
        return intersects(left, right);
    }

    private static boolean registryIdEqual(Object a, Object b, GraphDataContext context) {
        Set<String> left = ValueTagUtils.registryIdentities(a, context);
        Set<String> right = ValueTagUtils.registryIdentities(b, context);
        return !left.isEmpty() && intersects(left, right);
    }

    private static boolean componentsEqual(Object a, Object b, GraphDataContext context) {
        if (a instanceof Number numA && b instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue()) == 0;
        }
        if (a instanceof Entity entA && b instanceof Entity entB) {
            return entA.getUUID().equals(entB.getUUID());
        }
        if (a instanceof ItemStack stackA && b instanceof ItemStack stackB) {
            return ItemStack.isSameItemSameComponents(stackA, stackB);
        }

        Set<String> left = ValueTagUtils.registryIdentities(a, context);
        Set<String> right = ValueTagUtils.registryIdentities(b, context);
        if (!left.isEmpty() || !right.isEmpty()) {
            return intersects(left, right);
        }

        return Objects.equals(a, b);
    }

    private static boolean exactEqual(Object a, Object b) {
        if (a instanceof Number numA && b instanceof Number numB) {
            return Double.compare(numA.doubleValue(), numB.doubleValue()) == 0;
        }
        if (a instanceof Entity entA && b instanceof Entity entB) {
            return entA.getUUID().equals(entB.getUUID());
        }
        if (a instanceof ItemStack stackA && b instanceof ItemStack stackB) {
            return ItemStack.matches(stackA, stackB);
        }
        return Objects.equals(a, b);
    }

    private static boolean countMatches(Object a, Object b, String countMode, String compareMode) {
        if (MODE_EXACT.equals(compareMode) || COUNT_IGNORE.equals(countMode)) {
            return true;
        }
        if (!(a instanceof ItemStack stackA) || !(b instanceof ItemStack stackB)) {
            return true;
        }

        int left = stackA.getCount();
        int right = stackB.getCount();
        return switch (countMode) {
            case COUNT_EXACT -> left == right;
            case COUNT_AT_LEAST -> left >= right;
            case COUNT_AT_MOST -> left <= right;
            default -> true;
        };
    }

    private static boolean intersects(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return true;
            }
        }
        return false;
    }
}
