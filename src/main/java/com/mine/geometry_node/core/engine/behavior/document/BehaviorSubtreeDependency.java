package com.mine.geometry_node.core.engine.behavior.document;

import com.google.gson.annotations.SerializedName;

import java.util.LinkedHashMap;
import java.util.Map;

/** Future subtree dependency signature stored without introducing runtime semantics. */
public final class BehaviorSubtreeDependency {
    @SerializedName("asset_id")
    public String assetId = "";

    @SerializedName("input_mapping")
    public Map<String, String> inputMapping = new LinkedHashMap<>();

    @SerializedName("output_mapping")
    public Map<String, String> outputMapping = new LinkedHashMap<>();

    public void restoreDocumentDefaults() {
        if (assetId == null) assetId = "";
        if (inputMapping == null) inputMapping = new LinkedHashMap<>();
        if (outputMapping == null) outputMapping = new LinkedHashMap<>();
    }
}
