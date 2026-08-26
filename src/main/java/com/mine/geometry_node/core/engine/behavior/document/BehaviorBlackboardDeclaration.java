package com.mine.geometry_node.core.engine.behavior.document;

import com.google.gson.annotations.SerializedName;

/** Editable declaration; the compiler converts it into an immutable typed schema. */
public final class BehaviorBlackboardDeclaration {
    public String name = "";
    public String scope = "instance";
    public String type = "";
    public boolean writable = true;

    @SerializedName("default")
    public Object defaultValue;
}
