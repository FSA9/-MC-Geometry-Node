#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
#ifdef HAS_SKIN
in vec4 Joints;
in vec4 Weights;
layout(std140) uniform SkinPalette {
    mat4 JointMatrices[128];
    mat4 JointNormalMatrices[128];
};
#endif
#ifdef HAS_NORMAL
in vec3 Normal;
out vec3 normalView;
#endif
#ifdef HAS_TANGENT
in vec4 Tangent;
out vec4 tangentView;
#endif
#ifdef HAS_UV
in vec2 UV0;
out vec2 texCoord0;
#endif
#ifdef HAS_UV1
in vec2 UV1; out vec2 texCoord1;
#endif
#ifdef HAS_UV2
in vec2 UV2; out vec2 texCoord2;
#endif
#ifdef HAS_UV3
in vec2 UV3; out vec2 texCoord3;
#endif
#ifdef HAS_UV4
in vec2 UV4; out vec2 texCoord4;
#endif
#ifdef HAS_COLOR
in vec4 Color;
#endif

out vec4 vertexColor;
out vec3 positionView;

void main() {
    mat4 skin = mat4(1.0);
#ifdef HAS_SKIN
    skin = JointMatrices[int(Joints.x)] * Weights.x
         + JointMatrices[int(Joints.y)] * Weights.y
         + JointMatrices[int(Joints.z)] * Weights.z
         + JointMatrices[int(Joints.w)] * Weights.w;
#endif
    vec4 viewPosition = ModelViewMat * skin * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPosition;
    positionView = viewPosition.xyz;
    vec4 base = ColorModulator;
#ifdef HAS_COLOR
    base *= Color;
#endif
#ifdef HAS_NORMAL
#ifdef HAS_SKIN
    mat3 skinNormal = mat3(JointNormalMatrices[int(Joints.x)]) * Weights.x
                    + mat3(JointNormalMatrices[int(Joints.y)]) * Weights.y
                    + mat3(JointNormalMatrices[int(Joints.z)]) * Weights.z
                    + mat3(JointNormalMatrices[int(Joints.w)]) * Weights.w;
    normalView = normalize(transpose(inverse(mat3(ModelViewMat))) * skinNormal * Normal);
#else
    normalView = normalize(transpose(inverse(mat3(ModelViewMat))) * Normal);
#endif
#ifdef HAS_TANGENT
    // Tangents are directions and use the forward linear transform. Normals use the
    // inverse transpose above; applying that matrix to both is incorrect under scale.
#ifdef HAS_SKIN
    vec3 transformedTangent = mat3(ModelViewMat) * mat3(skin) * Tangent.xyz;
#else
    vec3 transformedTangent = mat3(ModelViewMat) * Tangent.xyz;
#endif
    vec3 orthogonalTangent = transformedTangent - normalView * dot(normalView, transformedTangent);
    tangentView = vec4(normalize(orthogonalTangent), Tangent.w);
#endif
#endif
    vertexColor = base;
#ifdef HAS_UV
    texCoord0 = UV0;
#endif
#ifdef HAS_UV1
    texCoord1 = UV1;
#endif
#ifdef HAS_UV2
    texCoord2 = UV2;
#endif
#ifdef HAS_UV3
    texCoord3 = UV3;
#endif
#ifdef HAS_UV4
    texCoord4 = UV4;
#endif
}
