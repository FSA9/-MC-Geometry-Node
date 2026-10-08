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
out vec3 modelViewPosition;
out vec2 modelFogDistances;
float decodeUv(ivec2 pair) { return uintBitsToFloat((uint(pair.x) & 65535u) | ((uint(pair.y) & 65535u) << 16u)); }

uniform sampler2D MaterialData;
flat out vec4 materialFactors0;
flat out vec4 materialFactors1;
flat out vec4 materialFactors2;
flat out vec4 materialFactors3;
out vec4 normalMrUv;
out vec2 aoUv;
float material(int i) {
    uvec4 bytes=uvec4(round(texelFetch(MaterialData,ivec2(i,0),0)*255.0));
    return uintBitsToFloat(bytes.r|(bytes.g<<8u)|(bytes.b<<16u)|(bytes.a<<24u));
}
vec2 transformedUv(int slot, vec2 uv0, vec2 uv1) {
    int i=14+slot*8;
    vec2 uv=material(i+1)<0.5?uv0:uv1;
    return vec2(material(i+2)*uv.x+material(i+3)*uv.y+material(i+6),material(i+4)*uv.x+material(i+5)*uv.y+material(i+7));
}
void prepareMaterial(vec2 uv0,vec2 uv1) {
    // Per-material fetches run per vertex, never dozens of times per visible pixel.
    materialFactors0=vec4(material(0),material(1),material(2),material(3));
    materialFactors1=vec4(material(4),material(5),material(6),material(7));
    materialFactors2=vec4(material(8),material(9),material(10),material(11));
    materialFactors3=vec4(material(12),material(13),material(30),0.0);
    baseUv=transformedUv(0,uv0,uv1);
    emissiveUv=transformedUv(1,uv0,uv1);
    normalMrUv=vec4(transformedUv(2,uv0,uv1),transformedUv(3,uv0,uv1));
    aoUv=transformedUv(4,uv0,uv1);
}

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    modelViewPosition=(ModelViewMat * vec4(Position,1.0)).xyz;
    modelColor = Color;
    prepareMaterial(UV0,vec2(decodeUv(EmissionU),decodeUv(EmissionV)));
    modelNormal = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);
    int light = int(PackedLight + 0.5);
    // Opaque native draws carry per-primitive lightmap coordinates in ModelOffset.
    // Other resident users keep their existing per-vertex light and brightness factor.
    ivec2 lightUv = ModelOffset.z > 0.5 ? ivec2(ModelOffset.xy)
        : ivec2(light & 65535, (light >> 16) & 65535);
    modelLight = sample_lightmap(Sampler2, lightUv).rgb;
    // Native resident draws store the camera-relative instance transform in TextureMat.
    // Material UV transforms are applied independently by prepareMaterial above.
    vec3 relativePosition = (TextureMat * vec4(Position, 1.0)).xyz;
    modelRelativePosition=relativePosition;
    modelWorldNormal=normalize(transpose(inverse(mat3(TextureMat)))*Normal);
    modelFogDistances.x = fog_spherical_distance(relativePosition);
    modelFogDistances.y = fog_cylindrical_distance(relativePosition);
}
