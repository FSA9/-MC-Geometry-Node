package com.mine.geometry_node.core.engine.system.model.diagnostic;

import java.io.IOException;
import java.util.Objects;

/** Stable machine-readable failure, with a source/JSON pointer, independent of the renderer. */
public final class ModelProblem extends IOException {
    public enum Code { INVALID_CONTAINER, INVALID_ACCESSOR, INVALID_SCENE, INVALID_SOURCE,
        UNSUPPORTED_REQUIRED_EXTENSION, BUDGET_EXCEEDED, CANCELLED, IO_FAILURE }
    private final Code code;
    private final String location;
    public ModelProblem(Code code, String location, String detail) {
        super(Objects.requireNonNull(code) + " at " + Objects.requireNonNull(location) + ": " + detail);
        this.code = code;
        this.location = location;
    }
    public Code code() { return code; }
    public String location() { return location; }
}
