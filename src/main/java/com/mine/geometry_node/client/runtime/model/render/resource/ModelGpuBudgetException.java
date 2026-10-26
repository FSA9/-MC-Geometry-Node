package com.mine.geometry_node.client.runtime.model.render.resource;

/** A residency limit prevented allocation; callers may defer and retry after fenced resources retire. */
public final class ModelGpuBudgetException extends IllegalStateException {
    public ModelGpuBudgetException(String message) { super(message); }
}
