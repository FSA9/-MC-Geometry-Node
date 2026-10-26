package com.mine.geometry_node.client.integration.ai.graph;

import com.google.gson.JsonObject;
import com.mine.geometry_node.client.feature.ai.command.CommandInvocationContext;
import com.mine.geometry_node.client.feature.ai.command.CommandResult;
import com.mine.geometry_node.client.feature.ai.graph.GraphPatch;
import com.mine.geometry_node.client.feature.ai.graph.GraphPatchCodec;
import com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchIdempotencyStore;
import com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchPlanner;
import com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchPlanner.PatchFailure;
import com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchPlanner.PlannedPatch;
import com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchPlanner.PlanningContext;
import com.mine.geometry_node.client.feature.graph.document.GraphDocumentManager;
import com.mine.geometry_node.client.feature.graph.document.GraphSession;
import com.mine.geometry_node.client.feature.graph.edit.EditorContext;
import com.mine.geometry_node.client.feature.graph.edit.FrameBoundsPolicy;
import com.mine.geometry_node.client.feature.graph.edit.commands.document.CmdReplaceGraphState;

import icyllis.modernui.core.Core;

import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchPlanner.canonicalGraphJson;
import static com.mine.geometry_node.client.feature.ai.graph.patch.GraphPatchPlanner.sha256;

/** Plans a patch against a snapshot, revalidates its revision, and commits it as one undoable command. */
public final class GraphPatchTransactionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(GraphPatchTransactionService.class);
    private static final Duration UI_HANDOFF_TIMEOUT = Duration.ofSeconds(30);

    private final GraphSession session;
    private final BoundGraphScope scope;
    private final java.util.function.BooleanSupplier targetValidator;
    private final GraphPatchIdempotencyStore idempotencyStore;

    public GraphPatchTransactionService(GraphSession session, BoundGraphScope scope) {
        this(session, scope, () -> true, new GraphPatchIdempotencyStore());
    }

    public GraphPatchTransactionService(GraphSession session, BoundGraphScope scope,
                                        java.util.function.BooleanSupplier targetValidator,
                                        GraphPatchIdempotencyStore idempotencyStore) {
        this.session = Objects.requireNonNull(session, "document");
        this.scope = Objects.requireNonNull(scope, "scope");
        this.targetValidator = Objects.requireNonNull(targetValidator, "targetValidator");
        this.idempotencyStore = Objects.requireNonNull(idempotencyStore, "idempotencyStore");
    }

    public CommandResult apply(GraphPatch patch, CommandInvocationContext.CancellationToken cancellation) {
        if (Minecraft.getInstance().isSameThread() || Core.isOnUiThread()) {
            return CommandResult.failure("THREAD_VIOLATION", "GraphPatch 事务预检不能阻塞客户端或 UI 线程");
        }
        cancellation = cancellation == null ? CommandInvocationContext.CancellationToken.NONE : cancellation;
        String requestedHash = sha256(GraphPatchCodec.toJson(patch));
        GraphPatchIdempotencyStore.Reservation reservation = idempotencyStore.reserve(
                patch.idempotencyKey(), requestedHash);
        if (reservation.limitReached()) {
            return CommandResult.failure("IDEMPOTENCY_LIMIT_REACHED",
                    "当前 PowerShell Run 的 GraphPatch 幂等键已达到上限，请重启 Run");
        }
        if (!reservation.hashMatches()) {
            return CommandResult.failure("IDEMPOTENCY_CONFLICT", "idempotency_key 已用于不同 GraphPatch");
        }
        GraphPatchIdempotencyStore.Entry transaction = reservation.entry();
        if (!reservation.owner()) return awaitExisting(transaction, cancellation);

        transaction.beginPlanning();
        try {
            PlanningContext snapshot = awaitPlanningUi(onUi(() -> capturePlanSnapshot(patch)), cancellation);
            PlannedPatch plan = new GraphPatchPlanner(snapshot).plan(patch, requestedHash, cancellation);
            if (cancellation.isCancelled()) {
                return complete(transaction, CommandResult.failure("CANCELLED", "GraphPatch 在提交前已取消"));
            }
            transaction.beginCommit();
            UiHandoff<CommandResult> commit = onUi(() -> commit(plan));
            commit.future.whenComplete((result, failure) -> idempotencyStore.complete(transaction,
                    failure == null ? result : executionFailure(failure)));
            return awaitCommit(transaction, cancellation);
        } catch (PatchFailure failure) {
            return complete(transaction, CommandResult.failure(failure.code(), failure.getMessage()));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return complete(transaction, CommandResult.failure("CANCELLED", "GraphPatch 调用被中断"));
        } catch (ExecutionException failure) {
            return complete(transaction, executionFailure(failure));
        } catch (RuntimeException failure) {
            return complete(transaction, executionFailure(failure));
        }
    }

    private PlanningContext capturePlanSnapshot(GraphPatch patch) {
        Core.checkUiThread();
        requireOpenAndBound(patch);
        String beforeJson = canonicalGraphJson(session.editorContext.getGraph());
        FrameBoundsPolicy frameBoundsPolicy = session.editorContext.frameBoundsPolicy();
        return new PlanningContext(beforeJson, root -> new EditorContext(root, frameBoundsPolicy),
                scope.groupPath(), registryAccess(), key -> Component.translatable(key).getString());
    }

    private CommandResult commit(PlannedPatch plan) {
        Core.checkUiThread();
        requireOpenAndBound(plan.patch());
        if (!sha256(GraphPatchCodec.toJson(plan.patch())).equals(plan.patchHash())) {
            throw fail("PATCH_HASH_MISMATCH", "事务预检后的 GraphPatch hash 不匹配");
        }
        if (!canonicalGraphJson(session.editorContext.getGraph()).equals(plan.beforeJson())) {
            throw fail("REVISION_CONFLICT", "事务执行期间蓝图内容已变化，请重新读取并规划");
        }
        String changeId = UUID.randomUUID().toString();
        boolean executed = session.editorContext.getCommandManager().execute(new CmdReplaceGraphState(
                session.editorContext, plan.beforeJson(), plan.afterJson(), changeId));
        if (!executed) throw fail("PATCH_NO_CHANGES", "GraphPatch 提交未产生变化");
        LOGGER.info("Graph patch committed: change={}, revision={}, operations={}",
                changeId, session.revision(), plan.patch().operations().size());
        JsonObject data = new JsonObject();
        data.addProperty("patch_hash", plan.patchHash());
        data.addProperty("change_id", changeId);
        data.addProperty("revision", session.revision());
        data.addProperty("operation_count", plan.patch().operations().size());
        return new CommandResult(true, "GRAPH_PATCH_APPLIED", "GraphPatch 已原子提交", data,
                List.of(), session.revision(), changeId, CommandResult.ClientAction.NONE);
    }

    private void requireOpenAndBound(GraphPatch patch) {
        if (!targetValidator.getAsBoolean()) throw fail("TARGET_CHANGED", "事务执行期间目标 Viewport、蓝图 Tab 或 Group Scope 已变化");
        if (!GraphDocumentManager.INSTANCE.getSessions().contains(session)) throw fail("GRAPH_SESSION_CLOSED", "绑定的蓝图会话已关闭");
        if (!session.sessionId().toString().equals(patch.session().id())) throw fail("GRAPH_SESSION_MISMATCH", "GraphPatch session_id 与当前 Run 不匹配");
        if (!scope.id().equals(patch.scope().id())) throw fail("GRAPH_SCOPE_MISMATCH", "GraphPatch scope_id 与当前 Run 不匹配");
        if (session.revision() != patch.expectedRevision().value()) throw fail("REVISION_CONFLICT", "蓝图 revision 已变化");
        if (scope.resolve(session.editorContext.getGraph()) == null) throw fail("GRAPH_SCOPE_CLOSED", "绑定的 Group Scope 已不存在");
    }

    private static <T> UiHandoff<T> onUi(Supplier<T> action) {
        UiHandoff<T> handoff = new UiHandoff<>();
        Runnable task = () -> handoff.run(action);
        if (Core.isOnUiThread()) {
            task.run();
        } else {
            try {
                if (!Core.getUiHandlerAsync().post(task)) {
                    handoff.failPending(new IllegalStateException("ModernUI queue is unavailable"));
                }
            } catch (RuntimeException failure) {
                handoff.failPending(failure);
            }
        }
        return handoff;
    }

    private static <T> T awaitPlanningUi(UiHandoff<T> handoff,
                                         CommandInvocationContext.CancellationToken cancellation)
            throws InterruptedException, ExecutionException {
        long deadline = System.nanoTime() + UI_HANDOFF_TIMEOUT.toNanos();
        while (true) {
            if (cancellation.isCancelled()) {
                handoff.cancelPending();
                throw fail("CANCELLED", "GraphPatch 等待编辑器 UI 时已取消");
            }
            try {
                return handoff.future.get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            }
            if (System.nanoTime() >= deadline) {
                handoff.cancelPending();
                throw fail("UI_UNAVAILABLE", "编辑器 UI 响应超时");
            }
        }
    }

    private CommandResult awaitExisting(GraphPatchIdempotencyStore.Entry transaction,
                                        CommandInvocationContext.CancellationToken cancellation) {
        return awaitTransaction(transaction, cancellation, false);
    }

    private CommandResult awaitCommit(GraphPatchIdempotencyStore.Entry transaction,
                                      CommandInvocationContext.CancellationToken cancellation) {
        return awaitTransaction(transaction, cancellation, true);
    }

    private CommandResult awaitTransaction(GraphPatchIdempotencyStore.Entry transaction,
                                           CommandInvocationContext.CancellationToken cancellation,
                                           boolean commitKnown) {
        long deadline = System.nanoTime() + UI_HANDOFF_TIMEOUT.toNanos();
        while (true) {
            try {
                return transaction.result().get(100, TimeUnit.MILLISECONDS);
            } catch (TimeoutException ignored) {
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return uncertainOrCancelled(transaction, commitKnown, "GraphPatch 调用被中断");
            } catch (ExecutionException failure) {
                return executionFailure(failure);
            }
            if (cancellation.isCancelled()) {
                return uncertainOrCancelled(transaction, commitKnown, "GraphPatch 调用已取消");
            }
            if (System.nanoTime() >= deadline) {
                return uncertainOrCancelled(transaction, commitKnown, "GraphPatch 等待最终结果超时");
            }
        }
    }

    private static CommandResult uncertainOrCancelled(GraphPatchIdempotencyStore.Entry transaction,
                                                       boolean commitKnown, String reason) {
        CommandResult completed = transaction.result().getNow(null);
        if (completed != null) return completed;
        GraphPatchIdempotencyStore.State state = transaction.state();
        boolean mayHaveCommitted = commitKnown || state == GraphPatchIdempotencyStore.State.COMMITTING
                || state == GraphPatchIdempotencyStore.State.UNKNOWN;
        if (!mayHaveCommitted) return CommandResult.failure("CANCELLED", reason);
        transaction.markUnknown();
        JsonObject data = new JsonObject();
        data.addProperty("transaction_state", "UNKNOWN");
        data.addProperty("retry_with_same_idempotency_key", true);
        return new CommandResult(false, "GRAPH_PATCH_STATUS_UNKNOWN",
                reason + "；提交可能仍在完成，请使用相同 idempotency_key 重试以获取最终结果",
                data, List.of(), null, null, CommandResult.ClientAction.NONE);
    }

    private CommandResult complete(GraphPatchIdempotencyStore.Entry transaction, CommandResult result) {
        idempotencyStore.complete(transaction, result);
        return result;
    }

    private static CommandResult executionFailure(Throwable failure) {
        Throwable cause = failure;
        while ((cause instanceof ExecutionException || cause instanceof java.util.concurrent.CompletionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof PatchFailure patchFailure) {
            return CommandResult.failure(patchFailure.code(), patchFailure.getMessage());
        }
        LOGGER.error("GraphPatch execution failed", cause);
        return CommandResult.failure("GRAPH_PATCH_INTERNAL_ERROR", "GraphPatch 执行失败");
    }

    private static RegistryAccess registryAccess() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }

    private static PatchFailure fail(String code, String message) { return new PatchFailure(code, message); }

    private enum UiHandoffState { PENDING, RUNNING, COMPLETED, CANCELLED }
    private static final class UiHandoff<T> {
        private final CompletableFuture<T> future = new CompletableFuture<>();
        private final AtomicReference<UiHandoffState> state = new AtomicReference<>(UiHandoffState.PENDING);

        private void run(Supplier<T> action) {
            if (!state.compareAndSet(UiHandoffState.PENDING, UiHandoffState.RUNNING)) return;
            try {
                future.complete(action.get());
            } catch (Throwable failure) {
                future.completeExceptionally(failure);
            } finally {
                state.set(UiHandoffState.COMPLETED);
            }
        }

        private boolean cancelPending() {
            if (!state.compareAndSet(UiHandoffState.PENDING, UiHandoffState.CANCELLED)) return false;
            future.cancel(false);
            return true;
        }

        private void failPending(Throwable failure) {
            if (!state.compareAndSet(UiHandoffState.PENDING, UiHandoffState.COMPLETED)) return;
            future.completeExceptionally(failure);
        }
    }
}
