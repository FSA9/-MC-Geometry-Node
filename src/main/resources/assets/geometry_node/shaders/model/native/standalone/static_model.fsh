#version 330

#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

layout(std140) uniform ModelMaterial {
    vec4 BaseColorFactor;
    vec4 EmissiveAndCutoff;
    vec4 PbrFactors;
    vec4 TexturePresence0;
    vec4 TexturePresence1;
    vec4 UvSlots0;
    vec4 UvSlots1;
    vec4 TextureTransforms[10];
};

in vec4 vertexColor;
in vec3 positionView;
#ifdef HAS_NORMAL
in vec3 normalView;
#endif
#ifdef HAS_TANGENT
in vec4 tangentView;
#endif
#ifdef HAS_UV
in vec2 texCoord0;
#endif
#ifdef HAS_UV1
in vec2 texCoord1;
#endif
#ifdef HAS_UV2
in vec2 texCoord2;
#endif
#ifdef HAS_UV3
in vec2 texCoord3;
#endif
#ifdef HAS_UV4
in vec2 texCoord4;
#endif
out vec4 fragColor;

const float PI = 3.141592653589793;

vec3 linearToSrgb(vec3 value) {
    value = max(value, vec3(0.0));
    bvec3 cutoff = lessThanEqual(value, vec3(0.0031308));
    return mix(1.055 * pow(value, vec3(1.0 / 2.4)) - 0.055, value * 12.92, cutoff);
}

#ifdef HAS_UV
vec2 physicalUv(int index) {
    if (index == 0) return texCoord0;
#ifdef HAS_UV1
    if (index == 1) return texCoord1;
#endif
#ifdef HAS_UV2
    if (index == 2) return texCoord2;
#endif
#ifdef HAS_UV3
    if (index == 3) return texCoord3;
#endif
#ifdef HAS_UV4
    if (index == 4) return texCoord4;
#endif
    return vec2(0.0);
}

vec2 transformedUv(int slot, int uvIndex) {
    vec4 first = TextureTransforms[slot * 2];
    vec2 scale = TextureTransforms[slot * 2 + 1].xy;
    vec2 scaled = physicalUv(uvIndex) * scale;
    float cosine = cos(first.z), sine = sin(first.z);
    return first.xy + mat2(cosine, sine, -sine, cosine) * scaled;
}

vec2 normalMapVectorForSourceUv(vec2 mapped, int slot) {
    vec4 first = TextureTransforms[slot * 2];
    vec2 scale = TextureTransforms[slot * 2 + 1].xy;
    float cosine = cos(first.z), sine = sin(first.z);
    // transformedUv uses J = R * S. A sampled normal is expressed in the
    // transformed UV parameterization, so map its covariant XY components
    // through inverse(J) before applying the source Mikk TBN.
    vec2 inverseRotated = mat2(cosine, -sine, sine, cosine) * mapped;
    return inverseRotated / scale;
}
#endif

float distributionGgx(float nDotH, float roughness) {
    float a2 = roughness * roughness;
    a2 *= a2;
    float d = nDotH * nDotH * (a2 - 1.0) + 1.0;
    return a2 / max(PI * d * d, 1.0e-5);
}

float geometrySchlick(float nDotV, float roughness) {
    float k = roughness + 1.0;
    k = k * k / 8.0;
    return nDotV / max(nDotV * (1.0 - k) + k, 1.0e-5);
}

void main() {
#ifdef MIRRORED_SINGLE_SIDED
    if (gl_FrontFacing) discard;
#endif
    vec4 base = BaseColorFactor * vertexColor;
#ifdef HAS_UV
    if (TexturePresence0.x > 0.5) base *= texture(Sampler0, transformedUv(0, int(UvSlots0.x)));
#endif
#ifdef ALPHA_OPAQUE
    base.a = BaseColorFactor.a;
#endif
#ifdef ALPHA_MASK
    if (base.a < EmissiveAndCutoff.a) discard;
#endif

    float metallic = PbrFactors.x;
    float roughness = PbrFactors.y;
#ifdef HAS_UV
    if (TexturePresence0.y > 0.5) {
        vec4 mr = texture(Sampler1, transformedUv(1, int(UvSlots0.y)));
        roughness *= mr.g;
        metallic *= mr.b;
    }
#endif
    roughness = clamp(roughness, 0.045, 1.0);
    metallic = clamp(metallic, 0.0, 1.0);

    vec3 normal = vec3(0.0, 0.0, 1.0);
#ifdef HAS_NORMAL
    normal = normalize(normalView);
#ifdef DOUBLE_SIDED
    bool logicalFront = gl_FrontFacing;
#ifdef MIRRORED
    logicalFront = !logicalFront;
#endif
    if (!logicalFront) normal = -normal;
#endif
#if defined(HAS_TANGENT) && defined(HAS_UV)
    if (TexturePresence0.z > 0.5) {
        vec3 tangent = normalize(tangentView.xyz);
        vec3 bitangent = normalize(cross(normal, tangent)) * tangentView.w;
        vec3 mapped = texture(Sampler2, transformedUv(2, int(UvSlots0.z))).xyz * 2.0 - 1.0;
        mapped.xy *= PbrFactors.z;
        mapped.xy = normalMapVectorForSourceUv(mapped.xy, 2);
        normal = normalize(mat3(tangent, bitangent, normal) * mapped);
    }
#endif
#endif

    vec3 viewDirection = normalize(-positionView);
    vec3 lightDirection = normalize(TextureMat[1].xyz);
    vec3 halfVector = normalize(viewDirection + lightDirection);
    float nDotL = max(dot(normal, lightDirection), 0.0);
    float nDotV = max(dot(normal, viewDirection), 1.0e-4);
    float nDotH = max(dot(normal, halfVector), 0.0);
    float vDotH = max(dot(viewDirection, halfVector), 0.0);
    vec3 f0 = mix(vec3(0.04), base.rgb, metallic);
    vec3 fresnel = f0 + (1.0 - f0) * pow(1.0 - vDotH, 5.0);
    float distribution = distributionGgx(nDotH, roughness);
    float geometry = geometrySchlick(nDotV, roughness) * geometrySchlick(nDotL, roughness);
    vec3 specular = distribution * geometry * fresnel / max(4.0 * nDotV * nDotL, 1.0e-4);
    vec3 diffuse = (1.0 - fresnel) * (1.0 - metallic) * base.rgb / PI;
    float worldLight = TextureMat[2].x;
    bool fullBright = TextureMat[2].y > 0.5;
    vec3 direct = (diffuse + specular) * nDotL * worldLight;

    // Analytic environment approximation; the lighting interface leaves room for future IBL resources.
    vec3 environment = base.rgb * (1.0 - metallic) * (0.08 + 0.12 * worldLight);
    environment += f0 * mix(0.18, 0.03, roughness) * (0.35 + 0.65 * worldLight);
    float occlusion = 1.0;
#ifdef HAS_UV
    if (TexturePresence0.w > 0.5) {
        float sampled = texture(Sampler3, transformedUv(3, int(UvSlots0.w))).r;
        occlusion = mix(1.0, sampled, PbrFactors.w);
    }
#endif
    vec3 emissive = EmissiveAndCutoff.rgb;
#ifdef HAS_UV
    if (TexturePresence1.x > 0.5) emissive *= texture(Sampler4, transformedUv(4, int(UvSlots1.x))).rgb;
#endif
    vec3 shaded = fullBright ? base.rgb : direct + environment * occlusion;
    fragColor = vec4(linearToSrgb(shaded + emissive), base.a);
}
