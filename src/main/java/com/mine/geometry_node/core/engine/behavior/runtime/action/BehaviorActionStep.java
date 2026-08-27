package com.mine.geometry_node.core.engine.behavior.runtime.action;

import com.mine.geometry_node.core.engine.behavior.contract.BehaviorResult;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** One action state-machine transition. */
public record BehaviorActionStep<S>(BehaviorResult result, @Nullable S state,
                                    @Nullable BehaviorActionFailure failure) {
    public BehaviorActionStep {
        Objects.requireNonNull(result, "result");
        if (result == BehaviorResult.RUNNING && state == null) {
            throw new IllegalArgumentException("A running action step requires state");
        }
        if (result == BehaviorResult.FAILURE && failure == null) {
            throw new IllegalArgumentException("A failed action step requires a failure reason");
        }
        if (result != BehaviorResult.FAILURE && failure != null) {
            throw new IllegalArgumentException("Only a failed action step may carry failure details");
        }
    }

    public static <S> BehaviorActionStep<S> running(S state) {
        return new BehaviorActionStep<>(BehaviorResult.RUNNING,
                Objects.requireNonNull(state, "state"), null);
    }

    public static <S> BehaviorActionStep<S> success() {
        return new BehaviorActionStep<>(BehaviorResult.SUCCESS, null, null);
    }

    public static <S> BehaviorActionStep<S> success(@Nullable S state) {
        return new BehaviorActionStep<>(BehaviorResult.SUCCESS, state, null);
    }

    public static <S> BehaviorActionStep<S> failure(String code, String detail) {
        return failure(null, code, detail);
    }

    public static <S> BehaviorActionStep<S> failure(@Nullable S state, String code, String detail) {
        return new BehaviorActionStep<>(BehaviorResult.FAILURE, state,
                new BehaviorActionFailure(code, detail));
    }
}
