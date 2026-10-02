#version 330
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 EmissionU;
in ivec2 EmissionV;
in vec3 Normal;
in float PackedLight;
uniform sampler2D Sampler2;
out vec4 modelColor;
out vec2 baseUv;
out vec2 emissiveUv;
out vec3 modelNormal;
out vec3 modelLight;
out vec3 modelRelativePosition;
out vec3 modelWorldNormal;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;
float decodeUv(ivec2 pair) { return uintBitsToFloat((uint(pair.x) & 65535u) | ((uint(pair.y) & 65535u) << 16u)); }
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    modelColor = Color;
    baseUv = UV0;
    emissiveUv = vec2(decodeUv(EmissionU), decodeUv(EmissionV));
    modelNormal = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);
    int light = int(PackedLight + 0.5);
    modelLight = sample_lightmap(Sampler2, ivec2(light & 65535, (light >> 16) & 65535)).rgb;
    // Native resident draws store the camera-relative instance transform in TextureMat.
    // UV transforms are already baked, so this does not change texture sampling.
    vec3 relativePosition = (TextureMat * vec4(Position, 1.0)).xyz;
    modelRelativePosition=relativePosition;
    modelWorldNormal=normalize(transpose(inverse(mat3(TextureMat)))*Normal);
    sphericalVertexDistance = fog_spherical_distance(relativePosition);
    cylindricalVertexDistance = fog_cylindrical_distance(relativePosition);
}
