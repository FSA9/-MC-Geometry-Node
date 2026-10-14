// Thin dielectric response for receivers without a shaderpack material BRDF.
// Returns unpremultiplied linear RGB; the host retains its original alpha blending.
vec3 nodeTransparentSurface(vec3 p,vec3 normal,vec3 geo,vec3 albedo,float opacity,float roughness,float f0) {
    if(opacity<=0.0001)return vec3(0.0);
    vec3 eye=nodeEyeOffset-p;
    float normalLength=dot(normal,normal),geoLength=dot(geo,geo),eyeLength=dot(eye,eye);
    if(!(normalLength>1e-12) || !(geoLength>1e-12) || !(eyeLength>1e-12)
            || isinf(normalLength) || isinf(geoLength) || isinf(eyeLength))return vec3(0.0);
    vec3 n=normal*inversesqrt(normalLength),g=geo*inversesqrt(geoLength),v=eye*inversesqrt(eyeLength);
    if(dot(n,v)<0.0)n=-n;
    if(dot(g,n)<0.0)g=-g;
    float nv=clamp(dot(n,v),0.001,1.0);
    float a=clamp(roughness*roughness,0.01,1.0),a2=a*a;
    float viewVisibility=sqrt(nv*nv*(1.0-a2)+a2);
    vec3 receiver=p+g*0.015;
    vec3 sum=vec3(0.0);
    for(int i=0;i<min(nodeLightCount,8);i++) {
      if(!nodeLightCanReach(i,p))continue;
      if(dot(nodeLights[i*12].xyz-p,n)+nodeLights[i*12+5].y< -0.0001)continue;
      int samples=nodeEmitterSamples(i);
      NodeEmitterDomain domain=nodeEmitterDomain(i,p);
      vec3 centerDelta=p-nodeLights[i*12].xyz;
      float centerDistanceSquared=dot(centerDelta,centerDelta);
      float attenuation=nodeRangeAttenuation(i,centerDistanceSquared);
      vec3 integrated=vec3(0.0);
      for(int s=0;s<samples;s++) {
        vec3 source;float weight;nodeEmitterSample(i,p,s,samples,domain,source,weight);
        if(weight<=0.0)continue;
        vec3 radiance=nodeSampleRadiancePrepared(i,p,source,centerDistanceSquared,attenuation)*weight;
        if(max(radiance.r,max(radiance.g,radiance.b))<=0.000001)continue;
        vec3 delta=source-p;
        float lightLength=dot(delta,delta);
        if(!(lightLength>1e-12) || isinf(lightLength))continue;
        vec3 l=delta*inversesqrt(lightLength);
        float nl=clamp(dot(n,l),0.0,1.0);
        if(nl<=0.0)continue;
        vec3 halfVector=l+v;
        float halfLength=dot(halfVector,halfVector);
        if(halfLength<1e-10)continue;
        vec3 h=halfVector*inversesqrt(halfLength);
        float nh=clamp(dot(n,h),0.0,1.0),vh=clamp(dot(v,h),0.0,1.0);
        float fresnel=f0+(1.0-f0)*pow(1.0-vh,5.0);
        float denominator=nh*nh*(a2-1.0)+1.0;
        float distribution=a2/max(3.14159265*denominator*denominator,1e-7);
        float visibility=0.5/max(nl*viewVisibility+nv*sqrt(nl*nl*(1.0-a2)+a2),1e-5);
        vec3 response=albedo*opacity*(1.0-fresnel)
                +vec3(distribution*visibility*fresnel/max(opacity,0.05));
        integrated+=radiance*nl*response*nodeSampleTransmission(i,receiver,source);
      }
      sum+=integrated/float(samples);
    }
    return sum;
}
