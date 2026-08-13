#version 330

#moj_import <minecraft:dynamictransforms.glsl>

#ifdef HAS_TEXTURE
uniform sampler2D Sampler0;
#endif
#ifdef HAS_EMISSIVE_TEXTURE
uniform sampler2D Sampler1;
#endif

in vec4 vertexColor;
#ifdef HAS_NORMAL
in vec3 normalView;
#endif
#ifdef HAS_UV
in vec2 texCoord0;
#endif
out vec4 fragColor;

vec3 linearToSrgb(vec3 value) {
    value = max(value, vec3(0.0));
    bvec3 cutoff = lessThanEqual(value, vec3(0.0031308));
    vec3 lower = value * 12.92;
    vec3 higher = 1.055 * pow(value, vec3(1.0 / 2.4)) - 0.055;
    return mix(higher, lower, cutoff);
}

vec2 textureTransform(vec2 uv, vec2 offset, float rotation, vec2 scale) {
    vec2 scaled = uv * scale;
    float cosine = cos(rotation);
    float sine = sin(rotation);
    return offset + mat2(cosine, sine, -sine, cosine) * scaled;
}

void main() {
#ifdef MIRRORED_SINGLE_SIDED
    if (gl_FrontFacing) discard;
#endif
    vec4 color = vertexColor;
#if defined(HAS_TEXTURE) && defined(HAS_UV)
    vec2 baseUv = textureTransform(texCoord0, TextureMat[0].yz, TextureMat[0].w,
            vec2(TextureMat[1].w, TextureMat[2].w));
    vec4 baseSample = texture(Sampler0, baseUv);
    color.rgb *= baseSample.rgb;
    color.a *= baseSample.a;
#endif
#ifdef ALPHA_OPAQUE
    color.a = ColorModulator.a;
#endif
#ifdef ALPHA_MASK
    if (color.a < TextureMat[0][0]) discard;
#endif
#ifdef HAS_NORMAL
    vec3 litNormal = normalize(normalView);
#ifdef DOUBLE_SIDED
    bool logicalFront = gl_FrontFacing;
#ifdef MIRRORED
    logicalFront = !logicalFront;
#endif
    if (!logicalFront) litNormal = -litNormal;
#endif
    float diffuse = max(dot(litNormal, normalize(TextureMat[1].xyz)), 0.0);
    color.rgb *= mix(1.0, 0.35 + diffuse * 0.65, TextureMat[2][0]);
#endif
    vec3 emissive = ModelOffset;
#if defined(HAS_EMISSIVE_TEXTURE) && defined(HAS_UV)
    vec2 emissiveUv = textureTransform(texCoord0, TextureMat[2].yz, TextureMat[3].x,
            TextureMat[3].yz);
    emissive *= texture(Sampler1, emissiveUv).rgb;
#endif
    fragColor = vec4(linearToSrgb(color.rgb + emissive), color.a);
}
