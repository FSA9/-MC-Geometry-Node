package com.mine.geometry_node.core.engine.system.marker;

/** Built-in semantic marker styles; addon types remain registered through MarkerRegistrationContext. */
public enum BuiltinMarkerTypes {
    DEFAULT("default", 0xFF55CFFF),
    OBJECTIVE("objective", 0xFFFFCF65),
    DANGER("danger", 0xFFFF655E),
    DESTINATION("destination", 0xFF65E2B0),
    ENTITY("entity", 0xFFE9ACFF),
    ENEMY("enemy", 0xFFFF526B),
    ATTACK("attack", 0xFFFF9166),
    DEFEND("defend", 0xFF79AEFF),
    LOOT("loot", 0xFFD9ABFF),
    HEALING("healing", 0xFF6DE4AC),
    REGROUP("regroup", 0xFF71DCE0),
    WATCH("watch", 0xFFFFD17B),
    INTERACT("interact", 0xFF8BD0FF),
    QUESTION("question", 0xFFF2DD91),
    RETREAT("retreat", 0xFFFFAF7A);

    private final String key;
    private final int color;
    BuiltinMarkerTypes(String key, int color) { this.key = key; this.color = color; }
    public String key() { return key; }
    public String id() { return "geometry_node:" + key; }
    public MarkerType type() {
        return new MarkerType(id(), "geometry_node.marker.type." + key, color, id());
    }
}
