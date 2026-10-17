package com.mine.geometry_node.client.history;

import java.util.function.Consumer;

/** A domain adapter exposing its independently owned history scope. */
public interface HistoryTarget {
    HistoryState historyState();
    HistoryOperation undo();
    HistoryOperation redo();
    AutoCloseable subscribeHistory(Consumer<HistoryState> listener);
}
