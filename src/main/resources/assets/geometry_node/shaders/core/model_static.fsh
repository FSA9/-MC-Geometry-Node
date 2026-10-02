#version 330
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
uniform sampler2D BaseTexture;
uniform sampler2D EmissionTexture;
uniform sampler2D MaterialData;
uniform sampler2D Sampler2;
uniform int geometry_node_nativeActive;
uniform vec2 geometry_node_nativeLight;
// GEOMETRY_NODE_NATIVE_LIGHT_DECLARATIONS
in vec4 modelColor;
in vec2 baseUv;
in vec2 emissiveUv;
in vec3 modelNormal;
in vec3 modelLight;
in vec3 modelRelativePosition;
in vec3 modelWorldNormal;
in float sphericalVertexDistance;
in float cylindricalVertexDistance;
out vec4 fragColor;
float material(int i) {
    uvec4 bytes = uvec4(round(texelFetch(MaterialData,ivec2(i,0),0) * 255.0));
    return uintBitsToFloat(bytes.r | (bytes.g << 8u) | (bytes.b << 16u) | (bytes.a << 24u));
}
vec3 toLinear(vec3 v) {
    return mix(v / 12.92,pow((v + 0.055) / 1.055,vec3(2.4)),greaterThan(v,vec3(0.04045)));
}
vec3 toDisplay(vec3 v) {
    v=max(v,vec3(0.0));
    return mix(v*12.92,1.055*pow(v,vec3(1.0/2.4))-0.055,greaterThan(v,vec3(0.0031308)));
}
void main() {
    vec4 tex = texture(BaseTexture,baseUv);
    vec4 base = vec4(toLinear(tex.rgb),tex.a) * modelColor * vec4(material(0),material(1),material(2),material(3));
    int mode = int(material(8));
    if(mode==1 && base.a < material(7)) discard;
    vec3 emission=toLinear(texture(EmissionTexture,emissiveUv).rgb)*vec3(material(4),material(5),material(6));
    vec3 rgb;
    if(material(9)>0.5) rgb=base.rgb+emission;
    else {
        vec3 n=normalize(gl_FrontFacing ? modelNormal : -modelNormal);
        float diffuse=minecraft_mix_light(Light0_Direction,Light1_Direction,n,vec4(1.0)).r;
        // The P1/P2 collector samples the lightmap per primitive. The P4 resident backend stores
        // full-bright vertices and supplies the spatial light factor through ColorModulator.
        vec3 lighting = modelLight*ColorModulator.a;
        if(geometry_node_nativeActive!=0 && geometry_node_lightCount>0) {
            vec3 worldNormal=normalize(gl_FrontFacing ? modelWorldNormal : -modelWorldNormal);
            vec3 receiver=modelRelativePosition+worldNormal*0.01;
            float blockLight=geometry_node_placedLight(receiver,geometry_node_nativeLight.x);
            blockLight=geometry_node_mergedHeldLight(receiver,blockLight);
            float skyLight=geometry_node_nativeLight.y;
            lighting=sample_lightmap(Sampler2,ivec2(clamp(vec2(blockLight,skyLight),0.0,1.0)*240.0)).rgb;
        }
        rgb=base.rgb*lighting*diffuse+emission;
    }
    vec4 outputColor=vec4(toDisplay(rgb),mode==2 ? base.a : 1.0)*vec4(ColorModulator.rgb,1.0);
    fragColor=apply_fog(outputColor,sphericalVertexDistance,cylindricalVertexDistance,FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,FogColor);
}
