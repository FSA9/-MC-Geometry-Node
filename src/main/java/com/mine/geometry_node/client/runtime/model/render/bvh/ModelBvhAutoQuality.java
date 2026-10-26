package com.mine.geometry_node.client.runtime.model.render.bvh;

/** Receiver quality never removes an instance from the occluder atlas. */
final class ModelBvhAutoQuality {
    static String declarations() {
        return """
                uniform int bvhAutoQuality;
                uniform int bvhManualSamples;
                uniform float bvhManualResolution;
                uniform int bvhGlobalLocalEnabled;
                uniform int bvhGlobalGiEnabled;
                uniform int bvhReceiverCount;
                uniform vec4 bvhReceiverQuality[128];
                int modelLightingReceiverSlotAt(vec3 receiver) { return -1; }
                void modelLightingSetReceiverUv(vec2 uv) {}
                float bvhReceiverModelDistance(vec3 receiver) {
                    int slot = modelLightingReceiverSlotAt(receiver);
                    return slot >= 0 && slot < bvhReceiverCount ? bvhReceiverQuality[slot].w : 1e30;
                }
                float bvhReceiverDistance(vec3 receiver) {
                    float distanceToModel = bvhReceiverModelDistance(receiver);
                    return distanceToModel < 1e29 ? distanceToModel : length(receiver);
                }
                bool bvhReceiverEnabled(vec3 receiver) {
                    int slot = modelLightingReceiverSlotAt(receiver);
                    if (slot >= 0 && slot < bvhReceiverCount) return bvhReceiverQuality[slot].x > 0.0 && bvhReceiverQuality[slot].y > 0.0;
                    return bvhGlobalLocalEnabled != 0 && (bvhAutoQuality != 0
                            ? bvhReceiverDistance(receiver) <= 128.0 : bvhManualSamples > 0 && bvhManualResolution > 0.0);
                }
                bool bvhGiReceiverEnabled(vec3 receiver) {
                    int slot = modelLightingReceiverSlotAt(receiver);
                    if (slot >= 0 && slot < bvhReceiverCount) return bvhReceiverQuality[slot].z > 0.5;
                    return bvhGlobalGiEnabled != 0;
                }
                int bvhDistanceSamples(float distanceToModel) {
                    return distanceToModel <= 32.0 ? 4 : distanceToModel <= 64.0 ? 3
                            : distanceToModel <= 96.0 ? 2 : distanceToModel <= 128.0 ? 1 : 0;
                }
                int bvhReceiverSamples(vec3 receiver, int manualSamples) {
                    int slot = modelLightingReceiverSlotAt(receiver);
                    if (slot >= 0 && slot < bvhReceiverCount) return int(bvhReceiverQuality[slot].x);
                    if (bvhGlobalLocalEnabled == 0) return 0;
                    return bvhAutoQuality == 0 ? manualSamples : bvhDistanceSamples(bvhReceiverDistance(receiver));
                }
                float bvhModelResolution(float distanceToModel) {
                    // Unowned world surfaces keep full spatial resolution.
                    if (distanceToModel > 1e29) return 1.0;
                    if (distanceToModel > 128.0) return 0.0;
                    return 1.0 - clamp((distanceToModel - 32.0) / 192.0, 0.0, 0.5);
                }
                float bvhReceiverResolution(vec3 receiver) {
                    int slot = modelLightingReceiverSlotAt(receiver);
                    if (slot >= 0 && slot < bvhReceiverCount) return bvhReceiverQuality[slot].y;
                    if (bvhGlobalLocalEnabled == 0 || !bvhReceiverEnabled(receiver)) return 0.0;
                    if (bvhAutoQuality == 0) return bvhManualResolution;
                    return bvhModelResolution(bvhReceiverModelDistance(receiver));
                }
                """;
    }

    private ModelBvhAutoQuality() {}
}
