package com.mine.geometry_node.core.engine.behavior.document;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.Map;

/** Per-node parameter mapping for one subtree call site. */
public final class BehaviorSubtreeCall {
    /** Child input parameter -> caller INSTANCE blackboard key. */
    @SerializedName("input_mapping")
    public Map<String, String> inputMapping = new LinkedHashMap<>();

    /** Caller INSTANCE blackboard key -> child output parameter. */
    @SerializedName("output_mapping")
    public Map<String, String> outputMapping = new LinkedHashMap<>();

    public void restoreDocumentDefaults() {
        if (inputMapping == null) inputMapping = new LinkedHashMap<>();
        if (outputMapping == null) outputMapping = new LinkedHashMap<>();
    }
}
