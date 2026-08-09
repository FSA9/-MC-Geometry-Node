package com.mine.geometry_node.client.ui.shell.menu.api;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Instance-level registry containing definitions only, never View objects. */
public final class MainMenuRegistry {
    private static final Comparator<MainMenuDefinition> MENU_ORDER = Comparator
            .comparingInt(MainMenuDefinition::order)
            .thenComparing(MainMenuDefinition::id);
    private static final Comparator<MainMenuItemDefinition> ITEM_ORDER = Comparator
            .comparingInt(MainMenuItemDefinition::order)
            .thenComparing(MainMenuItemDefinition::id);

    private final Map<String, MainMenuDefinition> menus = new LinkedHashMap<>();
    private final Map<String, Map<String, MainMenuItemDefinition>> items = new LinkedHashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();

    public void registerMenu(MainMenuDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        if (menus.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate main menu: " + definition.id());
        }
        items.put(definition.id(), new LinkedHashMap<>());
        notifyChanged();
    }

    public void registerItem(String menuId, MainMenuItemDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        Map<String, MainMenuItemDefinition> menuItems = items.get(menuId);
        if (menuItems == null) {
            throw new IllegalArgumentException("Unknown main menu: " + menuId);
        }
        if (menuItems.putIfAbsent(definition.id(), definition) != null) {
            throw new IllegalArgumentException("Duplicate menu item " + menuId + ":" + definition.id());
        }
        notifyChanged();
    }

    public List<MainMenuDefinition> menus() {
        return menus.values().stream().sorted(MENU_ORDER).toList();
    }

    public List<MainMenuItemDefinition> items(String menuId) {
        Map<String, MainMenuItemDefinition> menuItems = items.get(menuId);
        if (menuItems == null) {
            return List.of();
        }
        return menuItems.values().stream().sorted(ITEM_ORDER).toList();
    }

    public MainMenuDefinition menu(String menuId) {
        return menus.get(menuId);
    }

    public Registration addChangeListener(Runnable listener) {
        Runnable checked = Objects.requireNonNull(listener, "listener");
        listeners.add(checked);
        return () -> listeners.remove(checked);
    }

    private void notifyChanged() {
        for (Runnable listener : List.copyOf(listeners)) {
            listener.run();
        }
    }

    @FunctionalInterface
    public interface Registration extends AutoCloseable {
        @Override
        void close();
    }
}
