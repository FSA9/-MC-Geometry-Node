package com.mine.geometry_node.client.runtime.quest;

import com.mine.geometry_node.core.network.packet.s2c.PacketQuestScreenSnapshot;

/** Screen operations supplied by client integration before network receivers are installed. */
public interface QuestScreenPresentation {
    boolean isActive();

    void openOrRefresh(PacketQuestScreenSnapshot snapshot);

    void close();

    void clear();
}
