#version 330
#moj_import <minecraft:light.glsl>
#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:sample_lightmap.glsl>
uniform sampler2D BaseTexture;
uniform sampler2D EmissionTexture;
uniform sampler2D MaterialData;
flat in vec4 materialFactors0;
flat in vec4 materialFactors1;
flat in vec4 materialFactors2;
flat in vec4 materialFactors3;
in vec4 normalMrUv;
in vec2 aoUv;
uniform sampler2D NormalTexture;
uniform sampler2D MetallicRoughnessTexture;
uniform sampler2D OcclusionTexture;
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
in vec3 modelViewPosition;
in vec2 modelFogDistances;
out vec4 fragColor;
float material(int i) {
    // Avoid dynamic indexing into vec4 varyings. Some NVIDIA GLSL drivers reject
    // the otherwise range guarded form as an out-of-bounds vector access at link time.
    if(i==0)return materialFactors0.x;
    if(i==1)return materialFactors0.y;
    if(i==2)return materialFactors0.z;
    if(i==3)return materialFactors0.w;
    if(i==4)return materialFactors1.x;
    if(i==5)return materialFactors1.y;
    if(i==6)return materialFactors1.z;
    if(i==7)return materialFactors1.w;
    if(i==8)return materialFactors2.x;
    if(i==9)return materialFactors2.y;
    if(i==10)return materialFactors2.z;
    if(i==11)return materialFactors2.w;
    if(i==12)return materialFactors3.x;
    if(i==13)return materialFactors3.y;
    uvec4 bytes=uvec4(round(texelFetch(MaterialData,ivec2(i,0),0)*255.0));
    return uintBitsToFloat(bytes.r|(bytes.g<<8u)|(bytes.b<<16u)|(bytes.a<<24u));
}
vec3 toLinear(vec3 v) {
    return mix(v / 12.92,pow((v + 0.055) / 1.055,vec3(2.4)),greaterThan(v,vec3(0.04045)));
}
vec3 toDisplay(vec3 v) {
    v=max(v,vec3(0.0));
    return mix(v*12.92,1.055*pow(v,vec3(1.0/2.4))-0.055,greaterThan(v,vec3(0.0031308)));
}
vec3 safeNormalize(vec3 v, vec3 fallback) { float squared=dot(v,v); return squared>1e-12 ? v*inversesqrt(squared) : fallback; }
// Analytic split-sum GGX environment BRDF (Lazarov/Karis, Physically Based Shading on Mobile).
// Vanilla provides lightmap irradiance but no reflection probe; use it as a uniform environment.
vec3 nativeEnvironmentReflectance(vec3 f0, float roughness, float nv) {
    vec4 r=roughness*vec4(-1.0,-0.0275,-0.572,0.022)+vec4(1.0,0.0425,1.04,-0.04);
    float a004=min(r.x*r.x,exp2(-9.28*nv))*r.x+r.y;
    vec2 integrated=vec2(-1.04,1.04)*a004+r.zw;
    return clamp(f0*integrated.x+integrated.y,0.0,1.0);
}
vec2 materialUv(int slot) {
    if(slot==0)return baseUv;
    if(slot==1)return emissiveUv;
    if(slot==2)return normalMrUv.xy;
    if(slot==3)return normalMrUv.zw;
    return aoUv;
}
// Cotangent frame handles mirrored UVs and transformed UV0/UV1 without changing resident buffers.
vec3 mappedNormal(vec3 n, vec3 position) {
    if(materialFactors3.z<0.5) return n;
    vec2 uv=materialUv(2);
    vec3 dp1=dFdx(position), dp2=dFdy(position);
    vec2 du1=dFdx(uv), du2=dFdy(uv);
    float det=du1.x*du2.y-du1.y*du2.x;
    if(abs(det)<1e-10) return n;
    vec3 tangent=(dp1*du2.y-dp2*du1.y)/det;
    vec3 bitangent=(-dp1*du2.x+dp2*du1.x)/det;
    tangent-=n*dot(n,tangent);
    if(dot(tangent,tangent)<1e-12) return n;
    tangent=normalize(tangent);
    float handed=dot(cross(n,tangent),bitangent)<0.0?-1.0:1.0;
    bitangent=cross(n,tangent)*handed;
    vec3 m=texture(NormalTexture,uv).rgb*2.0-1.0;
    m.xy*=material(12);
    return safeNormalize(tangent*m.x+bitangent*m.y+n*m.z,n);
}
void main() {
    vec4 tex = texture(BaseTexture,materialUv(0));
    // Keep coverage independent from the spatial light factor.  ColorModulator.a is
    // used below as the resident light multiplier; it must never attenuate BLEND
    // coverage (a dark hand-held glass surface is still the same glass surface).
    vec3 baseRgb = toLinear(tex.rgb) * modelColor.rgb * vec3(material(0),material(1),material(2));
    float baseAlpha = clamp(tex.a * modelColor.a * material(3), 0.0, 1.0);
    vec4 base = vec4(baseRgb,baseAlpha);
    int mode = int(material(8));
    if(mode==1 && base.a < material(7)) discard;
    vec3 emission=toLinear(texture(EmissionTexture,materialUv(1)).rgb)*vec3(material(4),material(5),material(6));
    vec3 rgb;
    if(material(9)>0.5) rgb=base.rgb;
    else {
        vec3 n=normalize(gl_FrontFacing ? modelNormal : -modelNormal);
        n=mappedNormal(n,modelViewPosition);
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
        vec4 mr=texture(MetallicRoughnessTexture,materialUv(3));
        float metallic=clamp(material(10)*mr.b,0.0,1.0);
        float roughness=clamp(material(11)*mr.g,0.045,1.0);
        float ao=mix(1.0,texture(OcclusionTexture,materialUv(4)).r,material(13));
        // Keep both direct GGX and indirect reflection: removing metal's diffuse term without
        // an environment contribution makes glTF's default white, fully metallic material black.
        vec3 viewDir=safeNormalize(-modelViewPosition,n);
        vec3 f0=mix(vec3(0.04),base.rgb,metallic), spec=vec3(0.0);
        float nv=max(dot(n,viewDir),0.001), a=roughness*roughness, a2=a*a;
        for(int j=0;j<2;j++) {
            vec3 l=normalize(j==0?Light0_Direction:Light1_Direction), h=safeNormalize(l+viewDir,n);
            float nl=max(dot(n,l),0.0), nh=max(dot(n,h),0.0), vh=max(dot(viewDir,h),0.0);
            float denom=nh*nh*(a2-1.0)+1.0;
            float distribution=a2/max(3.14159265*denom*denom,1e-6);
            float k=(roughness+1.0)*(roughness+1.0)*0.125;
            float visibility=(nv/(nv*(1.0-k)+k))*(nl/(nl*(1.0-k)+k));
            vec3 fresnel=f0+(1.0-f0)*pow(1.0-vh,5.0);
            spec+=distribution*visibility*fresnel*nl/max(4.0*nv*nl,0.001)*0.3;
        }
        vec3 indirectSpecular=nativeEnvironmentReflectance(f0,roughness,nv)*ao;
        rgb=(base.rgb*(1.0-metallic)*diffuse*ao+spec+indirectSpecular)*toLinear(clamp(lighting,0.0,1.0))+emission;
        if(nodeLightCount>0) {
            vec3 worldNormal=normalize(gl_FrontFacing ? modelWorldNormal : -modelWorldNormal);
            if(mode==2 && metallic<0.5) {
                rgb+=nodeTransparentSurface(modelRelativePosition,worldNormal,worldNormal,base.rgb,baseAlpha,roughness,0.04);
            } else {
                vec3 directLight=nodeSurface(modelRelativePosition,worldNormal,vec2(0.0));
                rgb+=(base.rgb*(1.0-metallic)+f0)*directLight;
            }
        }
    }
    float outputAlpha = mode==2 ? baseAlpha : 1.0;
    vec4 outputColor=vec4(toDisplay(rgb),outputAlpha)*vec4(ColorModulator.rgb,1.0);
    fragColor=apply_fog(outputColor,modelFogDistances.x,modelFogDistances.y,FogEnvironmentalStart,FogEnvironmentalEnd,FogRenderDistanceStart,FogRenderDistanceEnd,FogColor);
}
