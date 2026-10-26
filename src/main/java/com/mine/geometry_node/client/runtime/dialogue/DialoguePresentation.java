package com.mine.geometry_node.client.runtime.dialogue;

import com.mine.geometry_node.core.network.packet.s2c.PacketOpenDialogue;

import java.util.UUID;

/** Screen operations supplied by client integration before network receivers are installed. */
public interface DialoguePresentation {
    boolean supports(String styleId);

    void open(PacketOpenDialogue packet);

    void close(UUID sessionId);

    void clear();
}
