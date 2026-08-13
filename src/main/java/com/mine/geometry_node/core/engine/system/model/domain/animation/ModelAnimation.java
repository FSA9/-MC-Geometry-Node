package com.mine.geometry_node.core.engine.system.model.domain.animation;

import java.util.List;

public record ModelAnimation(String name, List<ModelAnimationSampler> samplers,
                             List<ModelAnimationChannel> channels) {
    public ModelAnimation {
        name = name == null ? "" : name;
        samplers = samplers == null ? List.of() : List.copyOf(samplers);
        channels = channels == null ? List.of() : List.copyOf(channels);
        if (samplers.isEmpty() || channels.isEmpty()) {
            throw new IllegalArgumentException("animation must contain samplers and channels");
        }
    }

    public float durationSeconds() {
        float duration = 0.0F;
        for (ModelAnimationChannel channel : channels) {
            duration = Math.max(duration, samplers.get(channel.samplerIndex()).endTime());
        }
        return duration;
    }
}
