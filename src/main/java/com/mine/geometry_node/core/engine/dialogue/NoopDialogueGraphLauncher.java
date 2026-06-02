package com.mine.geometry_node.core.engine.dialogue;

/**
 * Placeholder launcher used until dialogue graph execution is implemented.
 */
public final class NoopDialogueGraphLauncher implements DialogueGraphLauncher {
    @Override
    public LaunchResult launch(DialogueSession session) {
        return LaunchResult.empty();
    }

    @Override
    public LaunchResult choose(DialogueSession session, DialogueChoicePayload choice) {
        return LaunchResult.empty();
    }
}
